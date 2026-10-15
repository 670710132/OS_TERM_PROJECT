/**
 * ค่าที่รับมาจาก command line
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้
 * เหตุผลที่แจกให้: ผู้ตรวจต้องสั่งรันทุกกลุ่มด้วยคำสั่งรูปแบบเดียวกัน
 * และต้องรู้ชัดว่ากำลังรันด้วยค่าใด จึงไม่มีค่า default ซ่อนอยู่เลย
 *
 * รูปแบบการใช้งาน:
 *   java Main jobs_standard.csv priority 3 1 2
 */
public final class Config { // ประกาศเป็น final ป้องกันไม่ให้คลาสอื่นนำไป extends เพิ่มเติม

    // ข้อความอธิบายวิธีใช้งาน (USAGE) สำหรับแสดงเมื่อผู้ใช้ป้อน Argument ไม่ถูกต้อง
    public static final String USAGE =
            "Usage: java Main <workload.csv> <fcfs|priority> <workers> <printerPermits> <databasePermits>\n"
          + "  workload.csv      ไฟล์ชุดงานทดสอบ\n"
          + "  fcfs | priority   นโยบายการจัดลำดับงาน\n"
          + "  workers           จำนวน Worker Thread (ตั้งแต่ 1 ขึ้นไป)\n"
          + "  printerPermits    จำนวนสิทธิ์ใช้ PRINTER พร้อมกัน (ตั้งแต่ 1 ขึ้นไป)\n"
          + "  databasePermits   จำนวนสิทธิ์ใช้ DATABASE พร้อมกัน (ตั้งแต่ 1 ขึ้นไป)\n"
          + "\n"
          + "ตัวอย่าง: java Main jobs_standard.csv priority 3 1 2";

    /** นโยบายการจัดลำดับงาน (Enum กำหนดนโยบายคิว) */
    public enum Policy {
        FCFS,     // First-Come, First-Served (มาก่อนได้ทำก่อน)
        PRIORITY  // Priority-Based (จัดลำดับตามความสำคัญ)
    }

    // ฟิลด์เก็บค่าคอนฟิก ประกาศเป็น final เพื่อป้องกันการเปลี่ยนแปลงค่าหลังสร้าง Object เสร็จ
    public final String workloadPath;   // เส้นทาง/ชื่อไฟล์ workload CSV
    public final Policy policy;         // นโยบายการจัดลำดับงาน (FCFS/PRIORITY)
    public final int workers;           // จำนวน Worker Threads
    public final int printerPermits;    // จำนวน Permit ของ PRINTER
    public final int databasePermits;   // จำนวน Permit ของ DATABASE

    // Private Constructor: บังคับให้สร้างผ่าน static method parse() เท่านั้น
    private Config(String workloadPath, Policy policy, int workers,
                   int printerPermits, int databasePermits) {
        this.workloadPath = workloadPath;
        this.policy = policy;
        this.workers = workers;
        this.printerPermits = printerPermits;
        this.databasePermits = databasePermits;
    }

    /**
     * แปลง argument จาก main() เป็น Config
     *
     * @throws IllegalArgumentException เมื่อจำนวนหรือค่าของ argument ไม่ถูกต้อง
     *         ข้อความของ exception เหมาะกับการแสดงต่อผู้ใช้โดยตรง
     */
    public static Config parse(String[] args) {
        // ❓ [อาจารย์อาจถาม]: ถ้าป้อน Argument ไม่ครบ 5 ตัวจะเกิดอะไรขึ้น?
        // 💡 [คำตอบ]: จะโยน IllegalArgumentException แจ้งเตือนผู้ใช้ทันทีโดยไม่เริ่มรันโปรแกรม
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "ต้องใส่ argument ให้ครบ 5 ตัว แต่ได้รับ " + args.length + " ตัว");
        }

        // อ่านและตัดช่องว่างชื่อไฟล์ CSV
        String workloadPath = args[0].trim();
        if (workloadPath.isEmpty()) {
            throw new IllegalArgumentException("ชื่อไฟล์ workload เป็นค่าว่าง");
        }

        // แปลงข้อความนโยบายเป็น Enum Policy
        Policy policy;
        String policyText = args[1].trim().toLowerCase();
        if (policyText.equals("fcfs")) {
            policy = Policy.FCFS;
        } else if (policyText.equals("priority")) {
            policy = Policy.PRIORITY;
        } else {
            throw new IllegalArgumentException(
                    "นโยบายต้องเป็น fcfs หรือ priority เท่านั้น แต่พบ \"" + args[1].trim() + "\"");
        }

        // ❓ [อาจารย์อาจถาม]: ดักจับตัวเลขติดลบหรือค่า 0 อย่างไร?
        // 💡 [คำตอบ]: ใช้เมธอด parseAtLeastOne ตรวจสอบว่าต้องเป็นตัวเลข >= 1 เสมอ
        int workers = parseAtLeastOne(args[2], "workers");
        int printerPermits = parseAtLeastOne(args[3], "printerPermits");
        int databasePermits = parseAtLeastOne(args[4], "databasePermits");

        return new Config(workloadPath, policy, workers, printerPermits, databasePermits);
    }

    // เมธอดสำหรับแปลง String เป็น Integer และตรวจสอบว่ามีค่าอย่างน้อย 1 ขึ้นไป
    private static int parseAtLeastOne(String text, String name) {
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " ต้องเป็นจำนวนเต็ม แต่พบ \"" + text.trim() + "\"");
        }
        if (value < 1) {
            throw new IllegalArgumentException(
                    name + " ต้องมีค่าตั้งแต่ 1 ขึ้นไป แต่พบ " + value);
        }
        return value;
    }

    /** ข้อความบรรทัดเดียวสำหรับบันทึกลง log ว่ารันด้วยค่าใด */
    public String describe() {
        return String.format("workload=%s policy=%s workers=%d printer=%d database=%d",
                workloadPath, policy.name().toLowerCase(), workers, printerPermits, databasePermits);
    }
}
