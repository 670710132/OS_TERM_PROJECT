/**
 * รับงานจาก JobGenerator แล้วจัดเข้า Ready Queue
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ (หัวข้อ 2 และ 4):
 *   - Scheduler เป็น Thread บังคับ ห้ามให้ JobGenerator ใส่งานลง Ready Queue โดยตรง
 *   - รับผิดชอบการจัดลำดับตามนโยบาย FCFS หรือ Priority
 *
 * ข้อควรคิด:
 *   - Scheduler รับงานจาก JobGenerator ผ่านอะไร และรอโดยไม่กิน CPU อย่างไร
 *   - เมื่อ JobGenerator ปล่อยงานครบแล้ว Scheduler รู้ได้อย่างไรว่าควรหยุด
 */
import java.util.concurrent.BlockingQueue;

/**
 * รับงานจาก JobGenerator แล้วส่งต่อเข้า ReadyQueue (Thread-safe)
 */
public class Scheduler extends Thread {

    /**
     * ⭐⭐⭐ [POISON PILL PATTERN]
     * วัตถุพิเศษสำหรับส่งสัญญาณบอก Scheduler ว่า JobGenerator ปล่อยงานครบทุกชิ้นแล้ว[cite: 4]
     */
    public static final Job POISON_PILL =
            new Job(
                    "__SCHEDULER_STOP__",
                    0,
                    Integer.MAX_VALUE,
                    0,
                    ResourceType.NONE,
                    0,
                    Integer.MAX_VALUE
            );

    private final BlockingQueue<Job> inputQueue; // คิวรับงานมาจาก JobGenerator (arrivalQueue)[cite: 4]
    private final ReadyQueue readyQueue;           // คิวพร้อมรันสำหรับ Worker[cite: 4]
    private final ProjectLogger logger;            // ตัวบันทึก Log[cite: 4]

    public Scheduler(
            BlockingQueue<Job> inputQueue,
            ReadyQueue readyQueue,
            ProjectLogger logger) {

        super("scheduler"); // ตั้งชื่อ Thread ว่า "scheduler"[cite: 4]

        this.inputQueue = inputQueue;
        this.readyQueue = readyQueue;
        this.logger = logger;
    }

    @Override
    public void run() {

        try {

            while (true) {

                /*
                 * ⭐⭐⭐ [NO BUSY-WAITING TAKE]
                 * รอรับงานจาก JobGenerator ผ่าน BlockingQueue
                 * ถ้าคิวว่าง Scheduler จะนอนรอโดยไม่กิน CPU[cite: 4]
                 */
                Job job = inputQueue.take();

                /*
                 * ⭐⭐⭐ [GRACEFUL SHUTDOWN CHECK]
                 * ถ้าได้รับ POISON_PILL แปลว่า JobGenerator ปล่อยงานครบแล้ว
                 * ให้สั่งปิด ReadyQueue แล้วจบ Thread ทันที[cite: 4]
                 */
                if (job == POISON_PILL) {

                    readyQueue.close(); // ปิด ReadyQueue และปลุก Worker ทุกตัว[cite: 4, 5]

                    return; // หลุดออกจากลูปเพื่อจบ Thread[cite: 4]
                }

                /*
                 * ส่งงานเข้า ReadyQueue (ReadyQueue จะจัดลำดับตาม FCFS/Priority อัตโนมัติ)[cite: 4, 5]
                 */
                readyQueue.add(job);

                logger.systemEvent(
                        job.id + " READY"
                );
            }

        } catch (InterruptedException e) {

            // หากโดน Interrupt ระหว่างรอคิว ให้คืนสถานะ Interrupt และสั่งปิด ReadyQueue เพื่อความปลอดภัย[cite: 4, 5]
            Thread.currentThread().interrupt();

            readyQueue.close();
        }
    }
}