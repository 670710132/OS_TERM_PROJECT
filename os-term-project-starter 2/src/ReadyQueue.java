/*
 * จัดเก็บ Job ที่พร้อมทำงาน และจัดลำดับว่า Worker จะหยิบ Job ไหนไปทำก่อน
 *
 * สิ่งที่คลาสนี้ต้องทำได้:
 *   - เก็บงานที่รอ Worker อยู่
 *   - หยิบงานถัดไปตามนโยบายที่เลือก (FCFS หรือ Priority)
 *   - ถูกเรียกจากหลาย Thread พร้อมกันได้อย่างปลอดภัย
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 4: priority = 1 สูงสุด เมื่อเท่ากันต้องมีกติกาตัดสินลำดับ (tie-break)
 *     ที่ตัดสินจากข้อมูลของ Job ไม่ขึ้นกับว่า Thread ใดเข้าถึงคิวก่อน
 *   - หัวข้อ 7: ห้ามวนลูปเช็กแบบกิน CPU (busy waiting) — Worker ที่ไม่มีงานทำ
 *     ต้องถูกพักไว้ ไม่ใช่วนถามซ้ำ ๆ
 *
 * จะออกแบบเป็นคลาสเดียวที่รับนโยบายเข้ามา หรือแยกเป็นสองคลาส
 * หรือใช้โครงสร้างข้อมูลสำเร็จรูปของ Java ก็ได้ ขอให้อธิบายเหตุผลได้ใน Demo
 */
/*
 * Thread-safe Ready Queue สำหรับ FCFS / Priority
 * ใช้ ReentrantLock + Condition ควบคุมการเข้าถึงคิวและปลุก Thread เองอย่างปลอดภัย
 */
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ReadyQueue {
    private final Config.Policy policy; // policy เก็บนโยบายการจัดลำดับงานที่เลือก เช่น FCFS หรือ Priority
    private final PriorityQueue<Job> queue; // โครงสร้างข้อมูลคิวหลักสำหรับจัดเรียง Job ตาม Comparator

    // ⭐ [SYNCHRONIZATION LOCKS] ใช้ ReentrantLock และ Condition ควบคุม Race Condition และจัดการ Blocking
    private final ReentrantLock lock = new ReentrantLock(); // lock ควบคุมไม่ให้หลาย Thread แก้ไขคิวพร้อมกัน
    private final Condition notEmpty = lock.newCondition(); // ใช้พักและปลุก Worker เมื่อคิวมีงาน
    private boolean closed = false; // บอกว่า ReadyQueue ถูกสั่งปิดแล้วหรือยัง

    public ReadyQueue(Config.Policy policy) {
        this.policy = policy;
        this.queue = new PriorityQueue<>(comparatorFor(policy));
        // ถ้าเลือก FCFS คิวจะเรียงตามเวลาเข้าระบบ แต่ถ้าเลือก Priority คิวจะเรียงตามระดับความสำคัญก่อน
    }

    
    // Method Comparator กำหนดกติกาว่า Job ไหนควรอยู่ก่อน โดยแบ่งเป็น 2 กรณี
    private static Comparator<Job> comparatorFor(Config.Policy policy) {

        if (policy == Config.Policy.FCFS) {
            // 1.FCFS Policy: arrivalMs มาก่อนได้ทำก่อน -> ถ้าเท่ากันใช้ sequence จาก CSV 
            return Comparator
                    .comparingLong((Job j) -> j.arrivalMs)
                    .thenComparingInt(j -> j.sequence);
        }

        // 2.Priority Policy: priority = 1 สำคัญสุด -> arrivalMs -> sequence
        return Comparator
                .comparingInt((Job j) -> j.priority)
                .thenComparingLong(j -> j.arrivalMs)
                .thenComparingInt(j -> j.sequence);
    }

    //ใส่งานเข้าคิว 
    public void add(Job job) {
        if (job == null) { // 1. ตรวจสอบว่า Job ไม่ใช่ null
            throw new IllegalArgumentException("job ห้ามเป็น null");
        }
        lock.lock(); // 2. ใช้ lock.lock() เพื่อเข้าล็อกก่อนแก้ไขคิว

        try {
            if (closed) { // 3. ตรวจสอบว่าคิวถูกปิดแล้วหรือยัง
                throw new IllegalStateException("ReadyQueue ถูกปิดแล้ว");
            }
            job.state = Job.State.READY; // 4. เปลี่ยนสถานะ Job เป็น READY
            queue.offer(job); // 5. เพิ่ม Job ด้วย queue.offer(job)

            notEmpty.signal(); // 6. เรียก notEmpty.signal() เพื่อปลุก Worker ที่กำลังรออยู่
        } finally {
            lock.unlock(); // 7. ปลดล็อกด้วย lock.unlock() ใน finally ป้องกัน Deadlock
        }
    }
    // หยิบงานถัดไป ถ้าไม่มีงานจะพักรอด้วย await() โดยไม่กิน CPU
    public Job take() throws InterruptedException {
        lock.lockInterruptibly(); // 1. เข้าล็อกด้วย lock.lockInterruptibly()
        try {
            // 2. ตรวจสอบว่าคิวว่างและยังไม่ปิดหรือไม่
            while (queue.isEmpty() && !closed) {
                notEmpty.await(); // 3. ถ้าคิวว่าง ให้พักรอด้วย notEmpty.await()
            }

            // 4. ถ้าคิวปิดแล้วและไม่มีงานเหลือ ให้คืนค่า null
            if (queue.isEmpty() && closed) {
                return null;
            }
            return queue.poll(); // 5. ถ้ามีงาน ให้ใช้ queue.poll() ดึง Job ที่มีความสำคัญสูงสุดออกจากคิว
        } finally {
            lock.unlock(); // 6. ปลดล็อกเสมอในสั่งสุดท้าย
        }
    }

    // ปิด ReadyQueue และสั่งปลุก Worker ทุกตัวที่รออยู่ให้ตื่นมาจบการทำงาน
    public void close() {
        lock.lock(); // 1. เข้าล็อก
        try {
            closed = true; // 2. กำหนด closed = true
            notEmpty.signalAll(); // 3. เรียก notEmpty.signalAll() เพื่อปลุก Worker ทุกตัวที่กำลังรอ
                                  // เพื่อให้ตื่นขึ้นมาเจอ closed = true แล้วคืน null ปิดตัวเอง
        } finally {
            lock.unlock(); // 4. ปลดล็อก
        }
    }

    public int size() { // ใช้ตรวจสอบจำนวน Job ที่กำลังรออยู่ในคิว โดยเข้าล็อกก่อนอ่านข้อมูลเพื่อให้การอ่านปลอดภัยเมื่อมีหลาย Thread ทำงานพร้อมกัน
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    // ใช้ตรวจสอบว่า ReadyQueue ถูกสั่งปิดแล้วหรือยัง โดยคืนค่า true หรือ false
    public boolean isClosed() {
        lock.lock();
        try {
            return closed;
        } finally {
            lock.unlock();
        }
    }
}