/**
 * Thread ที่รายงานสถานะระบบเป็นระยะ 
 * ข้อกำหนดจากโจทย์ (หัวข้อ 10):
 *   - รายงานประมาณทุก 1,000 ms ไม่ต้องแม่นตรงทุกครั้ง
 *   - อย่างน้อยต้องมี ready, running, completed และสถานะการใช้ resource
 *   - ข้อมูลที่อ่านต้องเป็น snapshot ที่ปลอดภัย
 *     โดยเฉพาะตัวนับ running ซึ่ง Worker หลายตัวเพิ่ม/ลดพร้อมกัน
 *   - ห้ามอ่าน collection หรือตัวนับที่กำลังถูกแก้ไขโดยไม่มีการป้องกัน
 *
 * ให้พิมพ์ผ่าน logger.monitor(ready, running, completed, resources.status())
 * เพื่อให้รูปแบบตรงกับกลุ่มอื่น
 *
 * ข้อควรคิด: ตัวนับ running ควรอยู่ที่ไหน ใครเป็นคนเพิ่มและลด
 * และจะอ่านพร้อมกับ ready กับ completed ให้เป็นภาพเดียวกันได้อย่างไร
 */
import java.util.List;
public class Monitor extends Thread {

    private final ReadyQueue readyQueue;     // คิวงานที่พร้อมรัน (Ready Queue)
    private final ResourceManager resources; // ตัวควบคุม Semaphore ของ Printer/Database
    private final Statistics statistics;     // ตัวสะสมสถิติมุมมองรวมของระบบ
    private final ProjectLogger logger;      // ตัวบันทึก Log กลาง

    // สวิตช์ควบคุมวงจรการทำงาน (ใช้ volatile เพื่อการันตี Memory Visibility ข้าม Thread)
    private volatile boolean running = true;

    public Monitor(
            ReadyQueue readyQueue,
            ResourceManager resources,
            Statistics statistics,
            ProjectLogger logger) {

        super("monitor"); // ตั้งชื่อ Thread ว่า "monitor" สำหรับแสดงใน Log

        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {

        try {

            while (running) {

                // 1. อ่านค่า Snapshot สถานะภาพรวมของระบบ ณ วินาทีนั้นอย่างปลอดภัย (Thread-safe)
                logger.monitor(
                        readyQueue.size(),           // จำนวนงานที่รอรันในคิว
                        statistics.runningCount(),   // จำนวนงานที่กำลังรันอยู่บน Worker[cite: 5]
                        statistics.completedCount(), // จำนวนงานที่ทำเสร็จแล้ว[cite: 5]
                        resources.status()           // สถานะสิทธิ์ Semaphore ของ Resource[cite: 5]
                );

                // 2. นอนรอประมาณ 1 วินาที (1,000 ms) ก่อนพิมพ์รายงานครั้งถัดไป[cite: 5]
                Thread.sleep(1000L);
            }

        } catch (InterruptedException e) {

            // หากโดน interrupt() จาก shutdown() ขณะ sleep ให้คืนสถานะเพื่อจบ Thread ทันที[cite: 5]
            Thread.currentThread().interrupt();
        }
    }

    /**
     * [Graceful Shutdown] สั่งหยุดการทำงานของ Monitor Thread[cite: 5]
     */
    public void shutdown() {

        running = false; // 1. สับสวิตช์ปิดลูป[cite: 5]

        interrupt();    // 2. ปลุก Thread ทันทีหากกำลัง sleep อยู่ ไม่ต้องจอดค้าง[cite: 5]
    }
}