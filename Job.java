/**
 * ข้อมูลของงานหนึ่งชิ้น
 *
 * ฟิลด์ทั้งหมดในไฟล์นี้มาจากไฟล์ workload CSV โดยตรง และถูกกำหนดครั้งเดียว
 * ตอนโหลด จึงประกาศเป็น final และปลอดภัยเมื่อหลาย Thread อ่านพร้อมกัน
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก แต่ต่างจากไฟล์อื่นตรงที่
 * นักศึกษา "ต้องแก้" โดยเพิ่มฟิลด์ของตัวเองในส่วน TODO ด้านล่าง
 */
public class Job {

    // 🚨 [FINAL FIELDS] ข้อมูลตั้งต้นที่อ่านมาจาก CSV ( immutable / Thread-safe อ่านได้พร้อมกัน )
    public final String id;           // รหัสงาน เช่น J01
    public final long arrivalMs;      // เวลาที่ควรเข้าสู่ระบบตามแผนใน CSV (มิลลิวินาที)
    public final int priority;        // ระดับความสำคัญ (1 = สูงสุด, เลขยิ่งมากยิ่งสำคัญน้อย)
    public final long workMs;         // ระยะเวลางานหลัก (Thread.sleep)
    public final ResourceType resource; // ชนิดทรัพยากรที่ต้องใช้ (NONE, PRINTER, DATABASE)
    public final long resourceMs;     // ระยะเวลาถือครองทรัพยากร
    public final int sequence;        // ลำดับบรรทัดในไฟล์ CSV (ใช้ทำ Tie-breaker)

    /**
     * 🔥 [JOB STATE ENUM] สถานะเชิงตรรกะของ Job ในระบบ Simulation 
     * (แยกเป็นเอกเทศจาก java.lang.Thread.State)
     */
    public enum State {
        ARRIVED,          // งานถูกปล่อยเข้า arrivalQueue แล้ว
        READY,            // งานย้ายมาอยู่ใน ReadyQueue พร้อมรัน
        RUNNING,          // Worker รับงานไปทำกำลังรัน workMs
        WAITING_RESOURCE, // งานกำลังต่อคิวรอ Semaphore ของ Resource
        COMPLETED         // งานประมวลผลเสร็จสิ้นสมบูรณ์
    }

    /*
     * 🔥🔥🔥 [VOLATILE TIMESTAMP & STATE FIELDS]
     * ฟิลด์สำหรับเก็บเวลาจริงและสถานะที่เกิดขึ้นระหว่าง simulation
     * ใช้ volatile เพื่อการันตี Memory Visibility ข้าม Thread 
     * (เมื่อ Worker อัปเดตค่า Thread อื่นเช่น Monitor จะมองเห็นทันที)
     */
    public volatile long enteredAtMs = -1L;         // เวลาที่เข้าสู่ระบบจริง (ARRIVED)
    public volatile long startedAtMs = -1L;         // เวลาที่ Worker เริ่มรันงาน (START)
    public volatile long completedAtMs = -1L;       // เวลาที่งานเสร็จสมบูรณ์ (COMPLETE)

    public volatile long resourceWaitStartMs = -1L; // เวลาที่เริ่มเข้าคิวรอ Semaphore (WAIT)
    public volatile long resourceWaitMs = 0L;       // ระยะเวลารวมที่ต้องยืนรอ Semaphore (ACQUIRE - WAIT)

    public volatile State state = null;             // สถานะปัจจุบันของ Job

    // Constructor หลักสำหรับสร้าง Object Job
    public Job(String id, long arrivalMs, int priority, long workMs,
               ResourceType resource, long resourceMs, int sequence) {
        this.id = id;
        this.arrivalMs = arrivalMs;
        this.priority = priority;
        this.workMs = workMs;
        this.resource = resource;
        this.resourceMs = resourceMs;
        this.sequence = sequence;
    }

    // === Helper Methods สำหรับคำนวณ Metrics ตามโจทย์ (เพิ่มเติมเพื่อให้คำนวณง่ายขึ้น) ===

    /** Waiting Time = startedAtMs - enteredAtMs */
    public long getWaitingTime() {
        if (startedAtMs < 0 || enteredAtMs < 0) return 0;
        return startedAtMs - enteredAtMs;
    }

    /** Turnaround Time = completedAtMs - enteredAtMs */
    public long getTurnaroundTime() {
        if (completedAtMs < 0 || enteredAtMs < 0) return 0;
        return completedAtMs - enteredAtMs;
    }

    @Override
    public String toString() {
        return String.format("%s(priority=%d, work=%dms, %s)",
                id, priority, workMs,
                resource == ResourceType.NONE
                        ? "no resource"
                        : resource + " " + resourceMs + "ms");
    }
}