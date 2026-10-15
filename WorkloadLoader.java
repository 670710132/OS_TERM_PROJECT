import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// อ่านไฟล์ Workload CSV ตรวจสอบความถูกต้องของข้อมูล แล้วแปลงข้อมูลแต่ละแถวให้เป็น Job Object
public final class WorkloadLoader {

    private static final int COLUMN_COUNT = 6; // บังคับต้องมี 6 คอลัมน์

    // ทำไม Constructor ถึงเป็น private? --> ป้องกันการสร้าง Object จาก Utility Class
    private WorkloadLoader() {
    }

    // ⭐⭐⭐ [LOAD CSV METHOD] อ่านไฟล์ workload CSV และตรวจความถูกต้องอย่างละเอียด
    public static List<Job> load(Path path) throws IOException, WorkloadFormatException { // IOException = ข้อผิดพลาดเกี่ยวกับการอ่านไฟล์ เช่น ไม่พบไฟล์หรืออ่านไม่ได้
                                                                                          // WorkloadFormatException = ข้อมูลในไฟล์มีรูปแบบไม่ถูกต้อง
        List<Job> jobs = new ArrayList<>(); // jobs เก็บ Job ที่อ่านและตรวจสอบผ่านแล้ว
        Set<String> seenIds = new HashSet<>(); // seenIds เก็บ ID ที่พบแล้ว เพื่อป้องกันรหัสงานซ้ำ
        int lineNumber = 0;
        boolean firstDataLine = true;
        // ใช้ BufferedReader อ่านไฟล์ข้อความ โดยกำหนดการเข้ารหัสเป็น UTF-8 = มาตรฐานการเข้ารหัสอักขระ นคอมพิวเตอร์ ที่ใช้สำหรับแปลงตัวอักษร สัญลักษณ์ หรือภาษาแทบทุกภาษาบนโลกนี้ ให้กลาย เป็นชุดตัวเลขฐานสอง เพื่อให้คอมพิวเตอร์สามารถเก็บ แสดงผล และรับส่งข้อมูลได้อย่างถูกต้อง
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) { // อ่านไฟล์ทีละบรรทัดจนหมด พร้อมเพิ่มหมายเลขบรรทัดเพื่อใช้แจ้งตำแหน่งที่ข้อมูลผิดพลาด
                lineNumber++;

                // 1. ตัด UTF-8 BOM(ลำดับของไบต์พิเศษ (รหัส 0xEF, 0xBB, 0xBF)) ทิ้ง หากไฟล์โดนเซฟมาจากโปรแกรมอื่น
                if (lineNumber == 1 && !rawLine.isEmpty() && rawLine.charAt(0) == '\uFEFF') {
                    rawLine = rawLine.substring(1);
                }

                String line = rawLine.trim();
                // 2. ข้ามบรรทัดว่าง
                if (line.isEmpty()) {
                    continue;
                }

                // 3.. ข้ามบรรทัดหัวตาราง (Header) ถ้าคอลัมน์แรกขึ้นต้นด้วย "id,"
                if (firstDataLine && line.toLowerCase().startsWith("id,")) {
                    firstDataLine = false;
                    continue;
                }
                firstDataLine = false;

                // แปลงข้อความบรรทัดนั้นเป็น Object Job
                Job job = parseLine(line, lineNumber, jobs.size()); // ส่งข้อมูลหนึ่งบรรทัดไปให้ parseLine() ตรวจสอบและสร้างเป็น Job
                
                // รวจสอบ ID ซ้ำ
                if (!seenIds.add(job.id)) {
                    throw new WorkloadFormatException(lineNumber, "รหัสงาน \"" + job.id + "\" ซ้ำกับบรรทัดก่อนหน้า");
                }
                jobs.add(job);
            }
        }

        // ดักจับกรณีไฟล์ไม่มีข้อมูลเลย
        if (jobs.isEmpty()) {
            throw new WorkloadFormatException(0, "ไฟล์ " + path + " ไม่มีข้อมูลงานเลย");
        }
        
        // คืนค่า List ที่ห้ามแก้ไขเพิ่ม/ลบข้อมูลเพื่อความ Thread-safe
        return Collections.unmodifiableList(jobs);
    }

    /* ทางลัดสำหรับเรียกด้วยชื่อไฟล์เป็นข้อความ */
    public static List<Job> load(String path) throws IOException, WorkloadFormatException {
        return load(Path.of(path));
    }

    // แกะข้อมูลทีละคอลัมน์และตรวจสอบกฎความถูกต้อง
    private static Job parseLine(String line, int lineNumber, int sequence)
            throws WorkloadFormatException {

        String[] parts = line.split(",", -1); // แบ่งข้อความตามเครื่องหมายจุลภาค , เพื่อแยกข้อมูลทั้ง 6 คอลัมน์
        if (parts.length != COLUMN_COUNT) { // จากนั้นตรวจสอบจำนวนคอลัมน์
            throw new WorkloadFormatException(lineNumber,
                    "ต้องมี " + COLUMN_COUNT + " คอลัมน์ แต่พบ " + parts.length + " คอลัมน์");
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        String id = parts[0];
        if (id.isEmpty()) { // ต้องไม่เป็นค่าว่าง และต้องไม่ซ้ำ
            throw new WorkloadFormatException(lineNumber, "คอลัมน์ id เป็นค่าว่าง");
        }
        long arrivalMs = parseNonNegativeLong(parts[1], "arrivalMs", lineNumber); // ต้องเป็นจำนวนเต็มตั้งแต่ 0 ขึ้นไป
        int priority = parsePositiveInt(parts[2], "priority", lineNumber); //ต้องเป็นจำนวนเต็มตั้งแต่ 1 ขึ้นไป
        long workMs = parseNonNegativeLong(parts[3], "workMs", lineNumber); // ต้องเป็นจำนวนเต็มตั้งแต่ 0 ขึ้นไป

        ResourceType resource;
        try {
            resource = ResourceType.parse(parts[4]); // ต้องเป็นประเภท Resource ที่ระบบรองรับ
        } catch (IllegalArgumentException e) {
            throw new WorkloadFormatException(lineNumber, e.getMessage());
        }

        long resourceMs = parseNonNegativeLong(parts[5], "resourceMs", lineNumber); // ต้องสัมพันธ์กับประเภท Resource ที่ระบุ

        // ถ้าไม่ใช้ Resource (NONE) ค่า resourceMs ต้องเท่ากับ 0
        if (resource == ResourceType.NONE && resourceMs != 0) {
            throw new WorkloadFormatException(lineNumber,
                    "เมื่อ resource เป็น NONE ค่า resourceMs ต้องเป็น 0 แต่พบ " + resourceMs);
        }
        //ถ้าใช้ Resource เช่น PRINTER หรือ DATABASE ค่า resourceMs ต้องมากกว่า 0
        if (resource != ResourceType.NONE && resourceMs <= 0) {
            throw new WorkloadFormatException(lineNumber,
                    "เมื่อ resource เป็น " + resource + " ค่า resourceMs ต้องมากกว่า 0 แต่พบ " + resourceMs);
        }
        // เมื่อผ่านการตรวจสอบทั้งหมดแล้ว จึงสร้าง Job
        return new Job(id, arrivalMs, priority, workMs, resource, resourceMs, sequence);
    }

    /** เมธอดช่วยตรวจสอบตัวเลขเต็มบวก/ศูนย์ (>= 0) */
    private static long parseNonNegativeLong(String text, String column, int lineNumber) //ใช้ตรวจสอบตัวเลขที่ต้องมีค่าตั้งแต่ 0 ขึ้นไป ได้แก่ arrivalMs, workMs และ resourceMs
            throws WorkloadFormatException {
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องเป็นจำนวนเต็ม แต่พบ \"" + text + "\"");
        }
        if (value < 0) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องไม่ติดลบ แต่พบ " + value);
        }
        return value;
    }

    /** Helper ตรวจสอบจำนวนเต็มบวกตั้งแต่ 1 ขึ้นไป (>= 1) */
    private static int parsePositiveInt(String text, String column, int lineNumber) // ใช้ตรวจสอบค่า priority ซึ่งต้องเป็นจำนวนเต็มตั้งแต่ 1 ขึ้นไป
            throws WorkloadFormatException {
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องเป็นจำนวนเต็ม แต่พบ \"" + text + "\"");
        }
        if (value < 1) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องมีค่าตั้งแต่ 1 ขึ้นไป (1 คือสำคัญที่สุด) แต่พบ " + value);
        }
        return value;
    }
}