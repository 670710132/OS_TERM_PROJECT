/**
 * เกิดขึ้นเมื่อไฟล์ workload CSV มีรูปแบบหรือค่าที่ไม่ถูกต้อง (อาจารย์แจกให้ ไม่ต้องแก้ไข)
 *
 * ข้อความของ exception นี้ออกแบบมาให้แสดงต่อผู้ใช้ได้โดยตรง
 * โดยระบุหมายเลขบรรทัดที่มีปัญหาเสมอ[cite: 4]
 */
public class WorkloadFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    /** ⭐ [LINE NUMBER FIELD] หมายเลขบรรทัดในไฟล์ เริ่มนับที่ 1 (หรือ 0 ถ้าปัญหาไม่ผูกกับบรรทัดใด)[cite: 4] */
    private final int lineNumber;

    /**
     * ⭐⭐⭐ [CONSTRUCTOR WITH AUTO FORMATTING]
     * จัดฟอร์แมตข้อความแจ้งเตือนอัตโนมัติ ถ้าระบุบรรทัดมา จะขึ้นต้นด้วย "บรรทัดที่ X: "[cite: 4]
     */
    public WorkloadFormatException(int lineNumber, String reason) {
        super(lineNumber > 0
                ? "บรรทัดที่ " + lineNumber + ": " + reason // กรณีระบุบรรทัด[cite: 4]
                : reason);                                  // กรณีปัญหาภาพรวม ไม่ระบุบรรทัด[cite: 4]
        this.lineNumber = lineNumber;
    }

    /**
     * ดึงหมายเลขบรรทัดที่มีปัญหา[cite: 4]
     */
    public int getLineNumber() {
        return lineNumber; //[cite: 4]
    }
}