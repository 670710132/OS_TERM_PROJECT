import java.io.PrintStream;

/**
 * บันทึกเหตุการณ์ของระบบด้วยรูปแบบที่ตายตัว
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้
 * และไม่มีคะแนนผูกกับการจัดรูปแบบ log
 *
 * เหตุผลที่แจกให้:
 * 1. log ของทุกกลุ่มจะมีหน้าตาเหมือนกัน ทำให้ผู้ตรวจอ่านได้เร็วและเทียบกันได้
 * 2. การเขียนจากหลาย Thread ถูกป้องกันให้แล้ว บรรทัดจะไม่ซ้อนกัน
 * 3. now() ให้เวลาฐานเดียวกับที่ปรากฏใน log นักศึกษาจึงควรใช้ตัวนี้วัดผลด้วย
 * เพื่อให้ค่าที่รายงานกับ log ตรวจสอบย้อนกลับกันได้
 *
 * รูปแบบบรรทัด:
 * [ 1234 ms] [worker-2 ] JOB_STARTED job=J04 priority=1
 *
 * การเก็บ log ลงไฟล์ ให้ redirect ตอนรัน ไม่ต้องเขียนโค้ดเพิ่ม:
 * java Main jobs_standard.csv priority 3 1 2 > logs/standard_priority_w3.log
 */

public final class ProjectLogger {

    private final long startNanos; // บันทึกเวลาจุดเริ่มต้นระบบเป็น nanoseconds
    private final PrintStream out; // ปลายทางที่จะพิมพ์ Log ออกไป (ปกติคือ System.out)
    private final Object writeLock = new Object(); // ⭐ [LOCK OBJECT] ป้องกัน Thread พิมพ์ Log ชนกัน

    // สร้าง logger และเริ่มจับเวลาทันที
    public ProjectLogger() {
        this(System.out);
    }

    public ProjectLogger(PrintStream out) {
        this.out = out;
        this.startNanos = System.nanoTime(); // จับเวลาจุดเริ่มต้น ณ วินาทีสร้าง Logger
    }

    /**
     * ⭐⭐⭐ [CRITICAL METHOD: นาฬิกากลางของระบบ]
     * คำนวณเวลามิลลิวินาทีตั้งแต่งานเริ่ม ใช้เป็นนาฬิกาอ้างอิงเดียวของทั้งระบบ
     */
    public long now() {
        return (System.nanoTime() - startNanos) / 1_000_000L; // แปลง nano เป็น milli (ms)
    }

    // ---------- เหตุการณ์ระดับระบบ ----------

    public void systemStart(Config config) {
        write("SYSTEM_START", config.describe()); // บันทึก Log เริ่มระบบพร้อมค่า Config
    }

    public void systemEvent(String detail) {
        write("SYSTEM", detail); // บันทึก Log เหตุการณ์ทั่วไป
    }

    public void systemStop(int completedJobs, int totalJobs) {
        write("SYSTEM_STOP", "completed=" + completedJobs + "/" + totalJobs); // บันทึก Log ปิดระบบ
    }

    // ---------- เหตุการณ์ระดับ Job ----------

    public void jobArrived(Job job) {
        write("JOB_ARRIVED", detailOf(job)); // บันทึก Log เมื่อ Job เข้าสู่ระบบ (ARRIVED)
    }

    public void jobStarted(Job job) {
        write("JOB_STARTED", "job=" + job.id + " priority=" + job.priority); // บันทึก Log เมื่อ Worker เริ่มทำ
    }

    public void workFinished(Job job) {
        write("WORK_FINISHED", "job=" + job.id); // บันทึก Log เมื่อทำ workMs เสร็จ
    }

    public void resourceWaitStarted(Job job) {
        write("RESOURCE_WAIT", "job=" + job.id + " resource=" + job.resource); // บันทึก Log เมื่อเริ่มรอ Semaphore
    }

    public void resourceAcquired(Job job, long waitedMs) {
        write("RESOURCE_ACQUIRED",
                "job=" + job.id + " resource=" + job.resource + " waited=" + waitedMs + "ms"); // บันทึก Log เมื่อได้
                                                                                               // Semaphore
    }

    public void resourceReleased(Job job) {
        write("RESOURCE_RELEASED", "job=" + job.id + " resource=" + job.resource); // บันทึก Log เมื่อคืน Semaphore
    }

    public void jobCompleted(Job job) {
        write("JOB_COMPLETED", "job=" + job.id); // บันทึก Log เมื่อ Job เสร็จสมบูรณ์
    }

    // ---------- เหตุการณ์จาก Monitor ----------

    public void monitor(int ready, int running, int completed, String resourceStatus) {
        write("MONITOR", "ready=" + ready + " running=" + running
                + " completed=" + completed + " " + resourceStatus); // บันทึก Log ภาพรวม Snapshot จาก Monitor
    }

    // ---------- ภายใน (Internal Helper Methods) ----------

    private static String detailOf(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("job=").append(job.id)
                .append(" priority=").append(job.priority)
                .append(" work=").append(job.workMs).append("ms");
        if (job.resource != ResourceType.NONE) {
            sb.append(" resource=").append(job.resource)
                    .append('(').append(job.resourceMs).append("ms)");
        }
        return sb.toString();
    }

    /**
     * ⭐⭐⭐ [SYNCHRONIZED WRITE METHOD]
     * จัดฟอร์แมตข้อความและพิมพ์ลง PrintStream
     * ใช้ synchronized ป้องกันข้อความทับซ้อนกันเมื่อหลาย Thread เรียกใช้พร้อมกัน
     */
    private void write(String event, String detail) {
        String line = String.format("[%8d ms] [%-10s] %-18s %s",
                now(),
                shorten(Thread.currentThread().getName()),
                event,
                detail == null ? "" : detail);
        synchronized (writeLock) { // ล็อกเพื่อให้พิมพ์ทีละบรรทัดอย่างปลอดภัย
            out.println(line);
        }
    }

    private static String shorten(String name) {
        return name.length() <= 10 ? name : name.substring(0, 10);
    }
}