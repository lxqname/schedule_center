package com.deepexi.tt.schedule.center.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import com.deepexi.tt.schedule.center.dao.ITaskDao;
import com.deepexi.tt.schedule.center.domain.bo.Task;
import com.deepexi.tt.schedule.center.enums.HttpExceptionMessageEnums;
import com.deepexi.tt.schedule.center.enums.QueueCapacityEnums;
import com.deepexi.tt.schedule.center.enums.TaskStatusEnums;
import com.deepexi.tt.schedule.center.service.ITaskService;
import com.deepexi.tt.schedule.center.util.Constant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Function;

/**
 * @author 白猛
 */
@Service
@Component
public class TaskServiceImpl implements ITaskService, CommandLineRunner {

    private static BlockingQueue<Task> taskQueue = new PriorityBlockingQueue<>(QueueCapacityEnums.QUEUE_INITIAL_CAPACITY, Comparator.comparingLong(t -> t.getExecuteTime().getTime()));

//    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(3, 6, 60, TimeUnit.SECONDS, new PriorityBlockingQueue<Runnable>());

    @Autowired
    ITaskDao taskDao;


    Function<Task, HttpRequest> cvt = t -> {
        if (t == null) {
            return null;
        }
        String mt = Optional.ofNullable(t.getMethod()).orElse("").toUpperCase();
        HttpRequest request = null;
        switch (mt) {
            case Constant.METHOD_GET:
                request = HttpRequest.get(t.getUrl());
                break;
            case Constant.METHOD_POST:
                request = HttpRequest.post(t.getUrl()).body(t.getData());
                break;
            default:
                request = HttpRequest.get(t.getUrl());
        }
        return request;
    };


    @Override
    public Task save(Task task) {
        //判断并加入任务队列
        taskDao.save(task);
        judgeAndAddToTaskQueue(task);
        return task;
    }

    private void judgeAndAddToTaskQueue(Task task) {
        if (Optional.ofNullable(task).isPresent() && Optional.ofNullable(task.getExecuteTime()).isPresent()) {
            if (task.getExecuteTime().before(new Date(System.currentTimeMillis() + Constant.TASK_TIME_LIMITED_IN_MILLIS))) {
                taskQueue.add(task);
            }
        }
    }

    @Override
    @Scheduled(cron = "0/9 * * * * ? ")
    public void findTaskQueue() {
        //查询近期未处理的任务集合
        System.out.println(new Date(System.currentTimeMillis() + Constant.TASK_TIME_LIMITED_IN_MILLIS));
        List<Task> tasks = taskDao.findTaskQueue
                (new Date(System.currentTimeMillis() + Constant.TASK_TIME_LIMITED_IN_MILLIS),
                        TaskStatusEnums.TASK_STATUS_NOT_EXECUTED);
        tasks.parallelStream().forEach(task -> {
            //如果不在队列中，则加入队列
            if (!taskQueue.parallelStream().filter(t -> t.getId().equals(task.getId())).findAny().isPresent()) {
                System.out.println("provider : " + task);
                taskQueue.add(task);
                System.out.println("队列长度 ：" + taskQueue.size());

            }
        });


    }


    @Override
    public void consumeTaskQueue() {
        while (true) {
            try {
                Task task = taskQueue.take();
                System.out.println("consumer:" + task);
                if (task.getExecuteTime().before(new Date())) {
                    Integer status = executeRequest(cvt.apply(task))
                            ? TaskStatusEnums.TASK_STATUS_EXECUTED_SUCCESS
                            : TaskStatusEnums.TASK_STATUS_EXECUTED_FAILED;
                    task.setStatus(status);
                    taskDao.save(task);
                    System.out.println("consumer : success");
                } else {
                    taskQueue.add(task);
                }
                Thread.sleep(Constant.TASK_QUEUE_CONSUMER_THREAD_SLEEP_TIME_IN_MILLIS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean executeRequest(HttpRequest request) {
        int count = 0;
        while (true) {
            try {
                if (ObjectUtils.isEmpty(request)) {
                    throw new Exception(HttpExceptionMessageEnums.REQUEST_IS_NULL);
                }
                HttpResponse response = request.execute();
                if (ObjectUtils.isEmpty(response)) {
                    throw new Exception(HttpExceptionMessageEnums.RESPONSE_IS_NULL);
                }
                if (response.getStatus() == HttpStatus.HTTP_OK) {
                    System.out.println("ok");
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            count++;
            if (count >= Constant.REQUEST_TIME) {
                break;
            }
        }
        return false;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("启动队列消费者……");
        consumeTaskQueue();
    }
}
