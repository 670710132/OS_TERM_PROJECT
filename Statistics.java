/**
 * รวบรวมและคำนวณค่าที่ใช้วัดผลของการรันหนึ่งครั้ง
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้ (หัวข้อ 8):
 *   - Waiting Time, Turnaround Time, Throughput, Resource Wait Time
 *   - ต้องถูกอัปเดตจากหลาย Worker พร้อมกันได้อย่างปลอดภัย
 *   - ผลต้องสอดคล้องกับสมการตรวจสอบ:
 *       Turnaround = Waiting + workMs + Resource Wait + resourceMs
 *     ใช้สมการนี้ตรวจงานทีละชิ้นได้ว่าค่าไหนคำนวณผิด
 *
 * ข้อควรระวัง: ค่าเฉลี่ยของ Resource Wait ให้คิดเฉพาะงานที่ใช้ resource
 * ส่วนงานที่ resource = NONE ให้ถือว่า Resource Wait เป็น 0
 */
import java.util.List;

/**
 * Thread-safe statistics ของการรันหนึ่งครั้ง (ใช้อัปเดตและแสดงผลสรุป)
 */
public class Statistics {

    // ตัวแปรสะสมเวลารวมมิลลิวินาที
    private long totalWaitingMs = 0L;
    private long totalTurnaroundMs = 0L;
    private long totalResourceWaitMs = 0L;

    // ตัวแปรนับจำนวน Job
    private int completedJobs = 0;
    private int runningJobs = 0;
    private int resourceJobs = 0; // ⭐ นับเฉพาะ Job ที่ใช้ Resource เพื่อเอาไปเป็นตัวหาร avgResourceWait[cite: 4]

    /**
     * ⭐ Worker เริ่มทำ Job -> สั่งเพิ่มตัวนับงานที่กำลังรันอยู่ (+1)[cite: 4]
     */
    public synchronized void jobStarted() {
        runningJobs++;
    }

    /**
     * ⭐⭐⭐ [CRITICAL THREAD-SAFE RECORDING]
     * บันทึกคำนวณผลลัพธ์เมื่อ Job ทำเสร็จสมบูรณ์ 1 ชิ้น (ใช้ synchronized ป้องกัน Race Condition)[cite: 4]
     */
    public synchronized void recordCompletion(Job job) {

        /*
         * Waiting Time = เวลาเริ่มรันจริง - เวลาเข้าสู่ระบบจริง[cite: 4]
         */
        long waiting = job.startedAtMs - job.enteredAtMs;

        /*
         * Turnaround Time = เวลาทำงานเสร็จสิ้น - เวลาเข้าสู่ระบบจริง[cite: 4]
         */
        long turnaround = job.completedAtMs - job.enteredAtMs;

        totalWaitingMs += waiting;
        totalTurnaroundMs += turnaround;

        /*
         * ⭐ [RESOURCE WAIT FILTER]
         * คิด Resource Wait Time เฉพาะ Job ที่ใช้ Resource เท่านั้น[cite: 4]
         */
        if (job.resource != ResourceType.NONE) {

            totalResourceWaitMs += job.resourceWaitMs;
            resourceJobs++; // เพิ่มตัวหารเฉพาะงานที่ใช้ Resource[cite: 4]
        }

        completedJobs++; // เพิ่มจำนวนงานที่ทำเสร็จ[cite: 4]
        runningJobs--;   // ลดจำนวนงานที่กำลังรัน[cite: 4]
    }

    /**
     * อ่านจำนวน Job ที่ทำเสร็จแล้ว (Thread-safe)[cite: 4]
     */
    public synchronized int completedCount() {
        return completedJobs;
    }

    /**
     * อ่านจำนวน Job ที่กำลังรันอยู่บน Worker (Thread-safe)[cite: 4]
     */
    public synchronized int runningCount() {
        return runningJobs;
    }

    /**
     * ⭐⭐⭐ [PRINT SUMMARY & METRICS CALCULATION]
     * คำนวณค่าเฉลี่ยและพิมพ์สรุปผลลัพธ์ใส่ตาราง[cite: 4]
     */
    public synchronized void printSummary(
            List<Job> allJobs,
            long makespanMs) {

        int totalJobs = allJobs.size();

        // คำนวณค่าเฉลี่ย WT และ TAT (หารด้วย totalJobs ทั้งหมด)[cite: 4]
        long avgWaiting = totalJobs == 0 ? 0 : totalWaitingMs / totalJobs;
        long avgTurnaround = totalJobs == 0 ? 0 : totalTurnaroundMs / totalJobs;

        // ⭐ คำนวณค่าเฉลี่ย RW (หารเฉพาะ resourceJobs เท่านั้น!)[cite: 4]
        long avgResourceWait = resourceJobs == 0 ? 0 : totalResourceWaitMs / resourceJobs;

        /*
         * ⭐ [THROUGHPUT FORMULA]
         * Throughput = งานที่เสร็จ / (Makespan มิลลิวินาที / 1000.0) -> ได้หน่วย jobs/sec[cite: 4]
         */
        double throughput = makespanMs <= 0 ? 0.0 : completedJobs / (makespanMs / 1000.0);

        System.out.println();
        System.out.println("========== SUMMARY ==========");

        System.out.println(
                "Jobs                 : "
                        + completedJobs
                        + "/"
                        + totalJobs
        );

        System.out.println(
                "Avg Waiting Time     : "
                        + avgWaiting
                        + " ms"
        );

        System.out.println(
                "Avg Turnaround Time  : "
                        + avgTurnaround
                        + " ms"
        );

        System.out.printf(
                "Throughput           : %.2f jobs/sec%n",
                throughput
        );

        System.out.println(
                "Avg Resource Wait    : "
                        + avgResourceWait
                        + " ms"
        );

        System.out.println(
                "Makespan             : "
                        + makespanMs
                        + " ms"
        );

        System.out.println(
                "============================="
        );
    }
}