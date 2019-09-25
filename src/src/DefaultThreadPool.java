package src;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by VE on 2019/9/18.
 */
public class DefaultThreadPool<Job extends Runnable> implements ThreadPool {
    // 线程池最大限制数
    private static final int MAX_WORKER_NUMBERS = 16;
    // 线程池默认的数量
    private static final int DEFAULT_WORKER_NUMBERS = 8;
    // 线程池最小数量
    private static final int MIN_WORKER_NUMBERS = 1;
    // 这是一个工作列表，将会向里面插入工作
    private final LinkedList<Job> jobs = new LinkedList<Job>();
    // 工作者列表
    private final List<Worker> workers = Collections.synchronizedList(new ArrayList<Worker>());
    // 工作者线程的数量
    private int workerNum = DEFAULT_WORKER_NUMBERS;
    // 线程编号生成
    private AtomicLong threadNum = new AtomicLong();

    public DefaultThreadPool(){
        initializedWorkers(DEFAULT_WORKER_NUMBERS);
    }

    @Override
    public void execute(Runnable job) {
        if (job != null){
            synchronized (jobs){
                jobs.addLast((Job)job);
                jobs.notify();
            }
        }
    }


    @Override
    public void shutdown() {
        for (Worker worker: workers){
            worker.shutdown();
        }
    }

    @Override
    public void addWorkers(int num) {
        synchronized (jobs){
            if (num + this.workerNum > MAX_WORKER_NUMBERS){
                num = MAX_WORKER_NUMBERS;
            }
            initializedWorkers(num);
            this.workerNum = num;
        }
    }

    @Override
    public void removeWorker(int num) {
        synchronized (jobs){
            if (num >= this.workerNum){
                throw new IllegalArgumentException("beyond workNum");
            }

            int count = 0;
            while (count<num){
                workers.get(count).shutdown();
                count++;
            }
            this.workerNum -= count;
        }
    }

    @Override
    public int getJobSize() {
        return jobs.size();
    }

    // 初始化线程工作者
    private void initializedWorkers(int num){
        for (int i = 0; i < num; i++) {
            Worker worker = new Worker();
            workers.add(worker);
            Thread thread = new Thread(worker,"ThreadPool-Workder-" + threadNum.incrementAndGet());
            thread.start();
        }
    }


    // 工作者，负责消费任务
    class Worker implements Runnable{
        // 是否工作
        private volatile boolean running = true;

        @Override
        public void run() {
            while (running){
                Job job = null;
                synchronized (jobs){
                    while (jobs.isEmpty()){
                        try {
                            jobs.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    job = jobs.removeFirst();
                }
                if (job!=null){
                    try {
                        job.run();
                    }catch (Exception e){

                    }
                }
            }
        }

        public void shutdown(){
            running = false;
        }
    }

}