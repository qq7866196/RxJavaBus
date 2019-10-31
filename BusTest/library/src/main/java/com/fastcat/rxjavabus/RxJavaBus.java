package com.fastcat.rxjavabus;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by ws on 2017/8/31 0031.
 *
 */

public class RxJavaBus {
    /**
     * Log tag, apps may override it.
     */
    public static String TAG = "RxJavaBus";
    private final ArrayList<Class> stickyEvents;
    private final Map<Class, List<Subscription>> eventToSub;
    private final Map<Subscription, List<Object>> subToEvent;
    static volatile RxJavaBus defaultInstance;
    private static final Builder DEFAULT_BUILDER = new Builder();
    private ArrayList<Disposable> disposables;

    public static RxJavaBus getDefault() {
        if (defaultInstance == null) {
            synchronized (RxJavaBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new RxJavaBus();
                }
            }
        }
        return defaultInstance;
    }

    public RxJavaBus() {
        this(DEFAULT_BUILDER);
    }

    RxJavaBus(Builder builder) {
        eventToSub = new ConcurrentHashMap<>();
        stickyEvents = new ArrayList<>();
        disposables = new ArrayList<>();
        subToEvent = new ConcurrentHashMap<>();
    }

    public void register(@NonNull ISubscriber subscriber, @NonNull Class event) {
        if (subscriber == null || event == null) {
            throw new RxJavaBusException("subscriber or event is null");
        }
        synchronized (this) {
            subscribe(subscriber, event);
            //发送粘性事件
            for (Object stickyEvent : stickyEvents) {
                ArrayList<Subscription> arrayList = (ArrayList) eventToSub.get(stickyEvent);
                if (arrayList != null) {
                    for (Subscription subscription : arrayList) {
                        if (subscription.subscriber.equals(subscriber)) {
                            subscriber.onEvent(stickyEvent, "");
                        }
                    }
                }
            }
        }


    }

    public void register(@NonNull ISubscriber subscriber, @NonNull Class... events) {
        if (subscriber == null || events == null) {
            throw new RxJavaBusException("subscriber or events is null");
        }
        synchronized (this) {
            for (Class event : events) {
                register(subscriber, event);
            }
        }
    }

    private void subscribe(ISubscriber sub, Class event) {
        if (sub == null || event == null) {
            return;
        }

        ArrayList subList, eventList;
        Subscription subscription = new Subscription(sub);

        if (eventToSub.get(event) == null) {
            subList = new ArrayList();
        } else {
            subList = (ArrayList) eventToSub.get(event);
        }

        if (subToEvent.get(subscription) == null) {
            eventList = new ArrayList();
        } else {
            eventList = (ArrayList) subToEvent.get(subscription);
        }

        if (!subList.contains(subscription)) {
            subList.add(subscription);
        }

        if (!eventList.contains(event)) {
            //TODO 这个判断不严谨
            eventList.add(event);
        }

        eventToSub.put(event, subList);
        subToEvent.put(subscription, eventList);
    }

    public void post(Object event) {
        post(event, null, null);
    }

    //指定接收线程
    public void post(Object event, Scheduler receive, String tag) {
        post(event, null, receive, tag);
    }

    //指定发送和接收线程
    public void post(final Object event, Scheduler send, Scheduler receive, final String tag) {
        if (event == null) {
            throw new RxJavaBusException("params is null");
        }
        if (send == null) {
            send = Schedulers.computation();
        }
        if (receive == null) {
            receive = AndroidSchedulers.mainThread();
        }
        //不使用链式，就切换线程无效？
        Disposable disposable = Flowable.create(new FlowableOnSubscribe<Object>() {
            @Override
            public void subscribe(FlowableEmitter<Object> e) throws Exception {
                e.onNext(event);
                e.onComplete();
            }
        }, BackpressureStrategy.BUFFER)
                .subscribeOn(send)
                .observeOn(receive)
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) throws Exception {
                        if (eventToSub.get(o.getClass()) == null) {
                            return;
                        }
                        //根据线程设置，在该线程中完成以下操作,默认会在UI线程
                        List<Subscription> arrayList = Collections.synchronizedList(eventToSub.get(o.getClass()));

                        for (Subscription sub : arrayList) {
                            sub.subscriber.onEvent(o, tag);
                        }
                    }
                });
        disposables.add(disposable);
    }

    public void post(Object event, String action) {
        if (event == null) {
            throw new RxJavaBusException("event is null");
        }
        ArrayList<Subscription> arrayList = (ArrayList) eventToSub.get(event.getClass());
        if (arrayList != null) {
            Iterator iterator = arrayList.iterator();
            while (iterator.hasNext()) {
                Subscription sub = (Subscription) iterator.next();
                sub.subscriber.onEvent(event, action);
            }
        }
    }

    public void postSticky(Object event) {
        if (event == null) {
            throw new RxJavaBusException("event is null");
        }
        synchronized (stickyEvents) {
            stickyEvents.add(event.getClass());
        }
        post(event);
    }

    public void unregister(ISubscriber subscriber) {
        if (subscriber == null) {
            throw new RxJavaBusException("subscriber is null");
        }
        if (subToEvent.get(subscriber) != null) {
            ArrayList<Class> eventList = (ArrayList) subToEvent.get(subscriber);
            ArrayList subList;
            for (Class event : eventList) {
                if (eventToSub.get(event) == null) {
                    return;
                } else {
                    subList = (ArrayList) eventToSub.get(event);
                    subList.remove(subscriber);
                    eventToSub.put(event, subList);
                }
            }
            subToEvent.remove(subscriber);
        }
    }

    static class Builder {

    }

    /**
     * 移除指定eventType的Sticky事件
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }


    /**
     * 移除所有的Sticky事件
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    public void cancelAllPost() {
        if (disposables != null) {
            for (Disposable disposable : disposables) {
                disposable.dispose();
            }
        }
    }

    public void clearCache() {
        if (stickyEvents != null) {
            stickyEvents.clear();
        }
        if (eventToSub != null) {
            eventToSub.clear();
        }
        if (disposables != null) {
            for (Disposable disposable : disposables) {
                disposable.dispose();
            }
        }
    }

}
