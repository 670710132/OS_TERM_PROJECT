/**
 * Thread ที่ดึงงานจาก Ready Queue ไปทำจนเสร็จ
 *
 * ลำดับการทำงานของ Job หนึ่งชิ้น บังคับตามหัวข้อ 6 ของโจทย์:
 *   1. รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
 *   2. จำลองงานหลักด้วย Thread.sleep(job.workMs)
 *   3. ถ้า job.resource != NONE ให้บันทึกเวลาเริ่มรอ แล้ว acquire
 *   4. จำลองการถือครองด้วย Thread.sleep(job.resourceMs)
 *   5. release แล้วบันทึกเวลาจบ
 *
 * ห้ามสลับขั้นที่ 2 กับ 3 เพราะจะทำให้ผลของทุกกลุ่มเทียบกันไม่ได้
 *
 * จุดที่มักพลาด:
 *   - ถ้า exception หรือ interrupt เกิดขึ้นหลัง acquire แต่ก่อน release
 *     permit จะค้างถาวรและระบบจะแขวน ต้องออกแบบให้คืนได้เสมอ
 *   - Worker ต้องหยุดเองได้เมื่อไม่มีงานเหลือแล้ว ไม่ใช่วนรอตลอดไป
 */

// Worker ที่ดึงงานจาก ReadyQueue แล้วทำตาม lifecycle 5 ขั้นตอนอย่างเคร่งครัด
public class Worker extends Thread {

    private final ReadyQueue readyQueue;     // คิวพร้อมรันที่ Worker ไปดึงงาน
    private final ResourceManager resources; // ตัวคุม Semaphore ขอ/คืนกุญแจ resource
    private final Statistics statistics;     // ตัวบันทึกสถิติมุมมองรวม
    private final ProjectLogger logger;      // ตัวพิมพ์ Log ระบบ

    public Worker(
            String name,
            ReadyQueue readyQueue,
            ResourceManager resources,
            Statistics statistics,
            ProjectLogger logger) {

        super(name); // ⭐ ตั้งชื่อ Thread เช่น "worker-1", "worker-2"

        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {

        try {

            while (!Thread.currentThread().isInterrupted()) {

                // ⭐ [NO BUSY WAITING] ดึงงานจาก ReadyQueue ถ้าไม่มีงาน จะจอดรอคิวโดยไม่กิน CPU
                Job job = readyQueue.take();

                // ⭐⭐⭐ [GRACEFUL SHUTDOWN] null = queue ปิดแล้วและไม่มีงานเหลือ ให้จบ Worker Thread ทันที
                if (job == null) {
                    return;
                }

                // ประมวลผล Job ตามลำดับขั้นตอน
                processJob(job);
            }

        } catch (InterruptedException e) {

            // คืนสถานะ Interrupt เพื่อจบการทำงานอย่างปลอดภัย
            Thread.currentThread().interrupt();
        }
    }

    /**
     * ⭐⭐⭐ [STRICT 5-STEP LIFECYCLE PROCESS]
     * ทำงานหนึ่ง Job ตามลำดับที่โจทย์กำหนด ห้ามสลับขั้นตอนเด็ดขาด
     */
    private void processJob(Job job)
            throws InterruptedException {

         // ขั้นที่ 1. Worker รับ Job และบันทึกเวลาเริ่มรัน
        job.startedAtMs = logger.now(); // บันทึกเวลาเริ่มจริง
        job.state = Job.State.RUNNING;   // เปลี่ยนสถานะเป็น RUNNING

        statistics.jobStarted();          // อัปเดตตัวนับงานที่กำลังรันใน Statistics (+1)
        logger.jobStarted(job);

        // ขั้นที่ 2. จำลองการทำงานหลัก (workMs)
        Thread.sleep(job.workMs); // นอนจำลองเวลาทำงานหลัก
        logger.workFinished(job);
        boolean acquired = false; // ตัวแปรธงเช็คสถานะการถือครอง Resource

        try {
            //ขั้นที่ 3. ถ้าต้องใช้ resource -> เริ่มรอแล้ว acquire
            if (job.resource != ResourceType.NONE) {
                job.resourceWaitStartMs = logger.now();   // บันทึกเวลาเริ่มยืนรอ
                job.state = Job.State.WAITING_RESOURCE; // เปลี่ยนสถานะเป็น WAITING_RESOURCE
                logger.resourceWaitStarted(job);

                // ขอกุญแจ Semaphore (ถ้ากุญแจหมดจะจอดรอที่นี่)
                resources.acquire(job.resource);
                // ⭐ สำคัญมาก: ตั้ง true หลัง acquire สำเร็จเท่านั้น!
                acquired = true;

                long acquiredAtMs = logger.now(); // เวลาที่ได้กุญแจจริง
                job.resourceWaitMs = acquiredAtMs - job.resourceWaitStartMs; // คำนวณระยะเวลาที่ยืนรอ
                logger.resourceAcquired(
                        job,
                        job.resourceWaitMs
                );
                // ได้ resource แล้ว กลับมาเป็นสถานะ RUNNING
                job.state = Job.State.RUNNING;

                // ขั้นที่ 4. จำลองการถือครอง resource (resourceMs)
                Thread.sleep(job.resourceMs); // นอนจำลองช่วงใช้ Resource
            }

        } finally {

            /*
             * ⭐⭐⭐ [CRITICAL TRY-FINALLY: ขั้นที่ 5 (คืน resource)]
             * ถ้า acquire สำเร็จแล้ว ต้อง release เสมอใน finally
             * แม้ Thread.sleep จะถูก interrupt หรือเกิด Exception ก็ตาม
             */
            if (acquired) {
                resources.release(job.resource); // คืนกุญแจกลับเข้า Semaphore
                logger.resourceReleased(job);
            }
        }

        // ขั้นที่ 6. Job ประมวลผลเสร็จสมบูรณ์
        job.completedAtMs = logger.now(); // บันทึกเวลาเสร็จจริง
        job.state = Job.State.COMPLETED;  // เปลี่ยนสถานะเป็น COMPLETED
        logger.jobCompleted(job);
        // บันทึกคำนวณผลลัพธ์สถิติ (WT, TAT, RW)
        statistics.recordCompletion(job);
    }
}