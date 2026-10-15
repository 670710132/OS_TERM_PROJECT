/**
 * ชนิดของทรัพยากรร่วมที่ Job หนึ่งตัวอาจต้องใช้ (อาจารย์แจกให้ ไม่ต้องแก้ไข)
 *
 * ในเวอร์ชันบังคับ Job หนึ่งตัวใช้ทรัพยากรได้ไม่เกิน 1 ชนิด
 * ค่า NONE หมายถึงไม่ต้องใช้ทรัพยากรร่วมเลย
 */
// กำหนดประเภทของ Resource ที่ Job สามารถเรียกใช้งานได้ โดยมีทั้งหมด 3 ประเภท
public enum ResourceType {
    NONE,      // Job ไม่ต้องใช้ Resource
    PRINTER,   // Job ต้องใช้เครื่องพิมพ์
    DATABASE;  // Job ต้องใช้ฐานข้อมูล

    // แปลงข้อความจากไฟล์ CSV ให้เป็นค่า ResourceType ที่ระบบเข้าใจ

    public static ResourceType parse(String text) {
        // 1. ดักจับค่า null
        if (text == null) {
            throw new IllegalArgumentException("resource เป็นค่าว่าง");
        }
        
        // 2. ตัดช่องว่างส่วนเกิน และแปลงเป็นตัวพิมพ์ใหญ่ เพื่อเปรียบเทียบข้อความ
        String normalized = text.trim().toUpperCase(); // trim() ตัดช่องว่างด้านหน้าและด้านหลัง
        
        // 3. วนลูปเช็คกับค่า Enum ทั้งหมดที่มี (NONE, PRINTER, DATABASE)
        for (ResourceType type : values()) {
            if (type.name().equals(normalized)) {
                return type; // ถ้าเจอข้อความตรงกัน คืนค่า Enum ทันที
            }
        }
        
        // 4. ถ้าไม่ตรงกับค่าใดเลย ให้โยน Exception แจ้ง Error ทันที
        throw new IllegalArgumentException(
                "resource ต้องเป็น NONE, PRINTER หรือ DATABASE เท่านั้น แต่พบ \"" + text.trim() + "\"");
    }
}
/*
 * "PRINTER" --> ResourceType.PRINTER
 * "printer" --> ResourceType.PRINTER
 * "MOUSE" --> เกิด IllegalArgumentException
 */