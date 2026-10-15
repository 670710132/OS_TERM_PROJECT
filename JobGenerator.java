// สร้างงานตามเวลาที่กำหนด แล้วส่งงานเข้า Scheduler เพื่อจัดลำดับต่อครับ
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

 //Thread ทำหน้าที่ปล่อย Job เข้าสู่ระบบตามเวลา arrivalMs ของแต่ละงาน
public class JobGenerator extends Thread {

    private final List<Job> jobs;                   // ลิสต์เก็บรายการ Job ทั้งหมดที่จะต้องปล่อย
    private final BlockingQueue<Job> schedulerInput; // คิวส่งต่อ Job ไปยัง Scheduler Thread (arrivalQueue)
    private final ProjectLogger logger;             // Logger กลางสำหรับบันทึก Log และอ่านเวลาระบบ

    public JobGenerator(
            List<Job> jobs,
            BlockingQueue<Job> schedulerInput,
            ProjectLogger logger) {

        super("generator"); // ตั้งชื่อ Thread ว่า "generator" สำหรับแสดงใน Log

        this.jobs = new ArrayList<>(jobs); // คัดลอกลิสต์งานมาไว้ในตัวแปร Local
        this.schedulerInput = schedulerInput;
        this.logger = logger;
    }

    @Override
    public void run() {
    /*
    เมธอด run()
    1. เรียงรายการ Job
    2. รอจนถึงเวลาที่ Job ควรเข้าระบบ
    3. บันทึกเวลาที่ Job เข้าระบบจริง
    4. เปลี่ยนสถานะเป็น ARRIVED
    5. บันทึก Log
    6. ส่ง Job เข้า schedulerInput
    7. เมื่อส่งงานครบแล้ว ส่ง Poison Pill ให้ Scheduler
    */
        jobs.sort(
                Comparator
                        .comparingLong((Job j) -> j.arrivalMs) // เรียงลำดับ Job ตาม arrivalMs จากน้อย ---> มาก
                        .thenComparingInt(j -> j.sequence) // ถ้า arrivalMs เท่ากันให้ดู sequence (ลำดับบรรทัดใน CSV) เพื่อให้อ่านงานเรียงตามลำดับจริง
        );

        try {
            // วนลูปปล่อย Job ทีละตัวตามลำดับเวลาที่เรียงไว้
            for (Job job : jobs) {

                // 1. นอนรอจนกว่าจะถึงเวลา arrivalMs ของ Job ชิ้นนั้นจริง
                waitUntil(job.arrivalMs);

                // 2. บันทึกเวลาที่เข้าสู่ระบบจริง (enteredAtMs) และเปลี่ยนสถานะ Job เป็น ARRIVED
                job.enteredAtMs = logger.now();
                job.state = Job.State.ARRIVED;

                // พิมพ์ Log อีเวนต์ JOB_ARRIVED ลงในระบบ
                logger.jobArrived(job);

                // 3. โยน Job เข้า schedulerInput (arrivalQueue) ให้ Scheduler ดึงไปจัดคิวต่อ
                schedulerInput.put(job);
            }

            // [GRACEFUL SHUTDOWN: POISON PILL]
            // เมื่อปล่อยงานครบทุกชิ้นแล้ว ให้ส่ง POISON_PILL เข้าไปแจ้ง Scheduler ว่าส่งงานจบแล้ว
            schedulerInput.put(
                    Scheduler.POISON_PILL
            );

        } catch (InterruptedException e) {

            // หากถูกสั่ง Interrupt ระหว่างรอนอนหรือโยนงาน ให้คืนสถานะ Interrupt เพื่อจบ Thread อย่างปลอดภัย
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 🔥 [WAIT UNTIL HELPER METHOD] คำนวณเวลานอนรอเทียบกับนาฬิกากลาง (logger.now())
     * และสั่ง Thread.sleep นอนรอจนกว่าจะถึงเวลาเป้าหมาย targetMs
     */
    private void waitUntil(long targetMs)
            throws InterruptedException {

        while (true) {

            // คำนวณระยะเวลาที่เหลือ = เวลาเป้าหมาย - เวลาปัจจุบันของระบบ
            long remaining = targetMs - logger.now();

            // ถ้าถึงเวลาปล่อยงานแล้ว (เวลาเหลือน้อยกว่าหรือเท่ากับ 0) ให้หลุดออกจากฟังก์ชัน
            if (remaining <= 0) {
                return;
            }

            // นอนรอตามระยะเวลาที่เหลืออยู่
            Thread.sleep(remaining);
        }
    }
}