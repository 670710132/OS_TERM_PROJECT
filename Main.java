/**
 * จุดเริ่มต้นของโปรแกรม
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ส่วนที่เขียนไว้ให้แล้วคือการรับค่า การโหลด workload และการแสดง error
 * ซึ่งไม่ใช่สิ่งที่โครงงานนี้วัด ส่วนที่เหลือเป็น TODO ทั้งหมด
 *
 * วิธีรัน:
 *   java Main jobs_standard.csv priority 3 1 2
 */
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * จุดเริ่มต้นของโปรแกรม
 */
public class Main {

    public static void main(String[] args) {

        /*
         * ==================================================
         * 1. อ่าน command line
         *
         * สำคัญ:
         * ถ้า argument ผิด ห้ามเริ่ม Thread
         * ==================================================
         */
        Config config;

        try {

            config = Config.parse(args);

        } catch (IllegalArgumentException e) {

            System.err.println(
                    "ผิดพลาด: " + e.getMessage()
            );

            System.err.println();

            System.err.println(Config.USAGE);

            System.exit(1);
            return;
        }


        /*
         * ==================================================
         * 2. โหลด CSV
         *
         * ใช้ WorkloadLoader ของอาจารย์
         * ไม่ parse CSV เอง
         * ==================================================
         */
        ProjectLogger logger =
                new ProjectLogger();

        List<Job> jobs;

        try {

            logger.systemStart(config);

            jobs =
                    WorkloadLoader.load(
                            config.workloadPath
                    );

        } catch (WorkloadFormatException e) {

            System.err.println(
                    "ไฟล์ workload ผิดรูปแบบ — "
                            + e.getMessage()
            );

            System.exit(1);
            return;

        } catch (java.io.IOException e) {

            System.err.println(
                    "เปิดไฟล์ \""
                            + config.workloadPath
                            + "\" ไม่ได้"
            );

            System.err.println(
                    "ตรวจว่าไฟล์มีอยู่จริงและ path ถูกต้อง"
            );

            System.exit(1);
            return;
        }


        logger.systemEvent(
                "โหลดงานได้ "
                        + jobs.size()
                        + " ชิ้น"
        );


        /*
         * ==================================================
         * 3. สร้าง Shared Components
         * ==================================================
         */

        /*
         * ResourceManager ต้องเป็น object เดียว
         * สำหรับ Worker ทุกตัว
         */
        ResourceManager resources =
                new ResourceManager(
                        config.printerPermits,
                        config.databasePermits
                );


        /*
         * ReadyQueue ใช้ policy จาก command line
         */
        ReadyQueue readyQueue =
                new ReadyQueue(
                        config.policy
                );


        /*
         * Statistics ใช้ร่วมกันทุก Worker
         */
        Statistics statistics =
                new Statistics();


        /*
         * ช่องทาง Generator -> Scheduler
         */
        BlockingQueue<Job> schedulerInput =
                new LinkedBlockingQueue<>();


        /*
         * ==================================================
         * 4. สร้าง Threads
         * ==================================================
         */

        Scheduler scheduler =
                new Scheduler(
                        schedulerInput,
                        readyQueue,
                        logger
                );


        JobGenerator generator =
                new JobGenerator(
                        jobs,
                        schedulerInput,
                        logger
                );


        Monitor monitor =
                new Monitor(
                        readyQueue,
                        resources,
                        statistics,
                        logger
                );


        /*
         * สร้าง Worker ตาม config.workers
         */
        List<Worker> workers =
                new ArrayList<>();

        for (int i = 1;
             i <= config.workers;
             i++) {

            workers.add(
                    new Worker(
                            "worker-" + i,
                            readyQueue,
                            resources,
                            statistics,
                            logger
                    )
            );
        }


        /*
         * ==================================================
         * 5. Start Threads
         * ==================================================
         */

        scheduler.start();

        for (Worker worker : workers) {
            worker.start();
        }

        monitor.start();

        generator.start();


        /*
         * ==================================================
         * 6. รอให้ระบบทำงานเสร็จ
         *
         * ห้ามใช้ sleep เดาเวลา
         * ==================================================
         */
        try {

            /*
             * Generator ปล่อยงานครบ
             */
            generator.join();


            /*
             * Scheduler รับ POISON_PILL
             * แล้วปิด ReadyQueue
             */
            scheduler.join();


            /*
             * Worker จะทำงานที่เหลือจนหมด
             *
             * เมื่อ ReadyQueue ปิด + ว่าง
             * take() จะคืน null
             * Worker จึงจบเอง
             */
            for (Worker worker : workers) {
                worker.join();
            }


            /*
             * ตอนนี้ทุก Job เสร็จแล้ว
             * หยุด Monitor
             */
            monitor.shutdown();

            monitor.join();


        } catch (InterruptedException e) {

            /*
             * ถ้า Main ถูก interrupt
             * ส่ง interrupt ไปยังทุก Thread
             */
            Thread.currentThread().interrupt();

            generator.interrupt();
            scheduler.interrupt();

            for (Worker worker : workers) {
                worker.interrupt();
            }

            monitor.shutdown();


            /*
             * รอให้ทุก Thread จบจริง
             */
            try {

                generator.join();
                scheduler.join();

                for (Worker worker : workers) {
                    worker.join();
                }

                monitor.join();

            } catch (InterruptedException ignored) {

                Thread.currentThread().interrupt();
            }

            return;
        }


        /*
         * ==================================================
         * 7. คำนวณ Makespan
         * ==================================================
         *
         * ใช้ completedAtMs ของ Job
         * ไม่ใช้เวลาที่ Main ตื่นหลัง join
         */
        long makespanMs = 0L;

        for (Job job : jobs) {

            if (job.completedAtMs > makespanMs) {
                makespanMs =
                        job.completedAtMs;
            }
        }


        /*
         * ==================================================
         * 8. แสดง Statistics
         * ==================================================
         */
        statistics.printSummary(
                jobs,
                makespanMs
        );


        /*
         * ==================================================
         * 9. SYSTEM_STOP
         * ==================================================
         */
        logger.systemStop(
                statistics.completedCount(),
                jobs.size()
        );
    }
}