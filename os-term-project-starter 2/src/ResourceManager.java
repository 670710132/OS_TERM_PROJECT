/**
 * ควบคุมสิทธิ์การใช้ทรัพยากรร่วมของทั้งระบบ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 5: ใช้ Semaphore ควบคุม PRINTER และ DATABASE
 *     จำนวน permit มาจาก command line (Config)
 *     ในส่วนบังคับให้สร้าง Semaphore แบบ fair = true
 *   - Worker ทุกตัวต้องใช้ ResourceManager object เดียวกัน
 *   - หัวข้อ 7: permit ต้องไม่สูญหายหรือค้าง แม้เกิด exception
 *     หรือถูก interrupt ระหว่างถือ resource
 *
 * คำถามที่จะถูกถามใน Demo:
 *   - ทำไมต้อง fair = true และถ้าเปลี่ยนเป็น false จะเกิดอะไรขึ้น
 *   - ถ้า Thread ถูก interrupt หลัง acquire สำเร็จแต่ก่อน release
 *     โค้ดของกลุ่มยังคืน permit ได้หรือไม่
 */
import java.util.concurrent.Semaphore;

/**
 * ควบคุม PRINTER และ DATABASE ด้วย fair Semaphore (Thread-safe)
 */
public class ResourceManager {

    private final Semaphore printer;
    private final Semaphore database;

    private final int printerPermits;
    private final int databasePermits;

    public ResourceManager(int printerPermits, int databasePermits) {

        // ดักจับข้อมูล: จำนวน Permit ต้องมีอย่างน้อย 1 ขึ้นไป[cite: 5]
        if (printerPermits < 1 || databasePermits < 1) {
            throw new IllegalArgumentException("จำนวน permit ต้องไม่น้อยกว่า 1");
        }

        this.printerPermits = printerPermits;
        this.databasePermits = databasePermits;

        // ⭐⭐⭐ [FAIR SEMAPHORE] 
        // กำหนด fair = true เพื่อจัดคิว FIFO ป้องกัน Thread Starvation (การถูกแซงคิว)[cite: 5]
        this.printer = new Semaphore(printerPermits, true);
        this.database = new Semaphore(databasePermits, true);
    }

    /**
     * ⭐ [ACQUIRE METHOD]
     * ขอสิทธิ์ใช้ Resource (ถ้ากุญแจหมด Thread จะจอดรอคิวโดยไม่กิน CPU)[cite: 5]
     */
    public void acquire(ResourceType type) throws InterruptedException {

        switch (type) {

            case PRINTER:
                printer.acquire(); // ยื่นขอกุญแจ PRINTER[cite: 5]
                break;

            case DATABASE:
                database.acquire(); // ยื่นขอกุญแจ DATABASE[cite: 5]
                break;

            case NONE:
                break; // ถ้าไม่ต้องใช้ resource ให้ข้ามไป[cite: 5]

            default:
                throw new IllegalArgumentException("Unknown resource type: " + type);
        }
    }

    /**
     * ⭐ [RELEASE METHOD]
     * คืนสิทธิ์ Resource เข้าสู่ Semaphore (ต้องถูกเรียกใน try-finally ของ Worker เสมอ)[cite: 5]
     */
    public void release(ResourceType type) {

        switch (type) {

            case PRINTER:
                printer.release(); // คืนกุญแจ PRINTER[cite: 5]
                break;

            case DATABASE:
                database.release(); // คืนกุญแจ DATABASE[cite: 5]
                break;

            case NONE:
                break;

            default:
                throw new IllegalArgumentException("Unknown resource type: " + type);
        }
    }

    /**
     * ⭐⭐⭐ [THREAD-SAFE SNAPSHOT STATUS]
     * คำนวณจำนวนกุญแจที่ถูกใช้อยู่ แล้วส่งข้อความสถานะให้ Monitor นำไปพิมพ์ Log[cite: 5]
     * ตัวอย่างผลลัพธ์: "printer=1/1 database=0/2"[cite: 5]
     */
    public String status() {

        // กุญแจที่ใช้อยู่ = กุญแจทั้งหมด - กุญแจที่ว่างอยู่ (availablePermits)[cite: 5]
        int printerUsed = printerPermits - printer.availablePermits();
        int databaseUsed = databasePermits - database.availablePermits();

        return String.format(
                "printer=%d/%d database=%d/%d",
                printerUsed,
                printerPermits,
                databaseUsed,
                databasePermits
        );
    }
}