package com.smartmeeting.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 待办「申请延期/挂起」理由输入会话状态存储（进程内）。
 * <p>
 * 当责任人点击每日汇总卡片上的「申请延期」或「挂起」按钮后，机器人引导其回复一条消息作为理由。
 * 本存储跟踪该中间等待状态，使下一条文本消息能被路由为理由提交。
 * <p>
 * TTL 默认 5 分钟；超时自动失效。
 */
@Component
public class TodoReasonPendingStore {

    /**
     * 待输入理由的决策类型。
     */
    public enum Decision {
        /** 申请延期 */
        REQUEST_DELAY,
        /** 挂起 */
        REQUEST_BLOCK
    }

    private static final class Entry {
        final Decision decision;
        final String todoId;
        final long expiresAtMs;

        Entry(Decision decision, String todoId, long expiresAtMs) {
            this.decision = decision;
            this.todoId = todoId;
            this.expiresAtMs = expiresAtMs;
        }
    }

    /**
     * 待输入理由会话的快照（对外只读视图）。
     */
    public static final class PendingReason {
        private final Decision decision;
        private final String todoId;

        PendingReason(Entry e) {
            this.decision = e.decision;
            this.todoId = e.todoId;
        }

        public Decision getDecision() {
            return decision;
        }

        public String getTodoId() {
            return todoId;
        }
    }

    private final long ttlMs;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public TodoReasonPendingStore(
            @Value("${meeting.todo.reason-input-ttl-minutes:5}") long ttlMinutes) {
        this.ttlMs = ttlMinutes * 60L * 1000L;
    }

    private static String key(String openId, String chatId) {
        return openId + "|" + (chatId == null ? "" : chatId);
    }

    /**
     * 标记某用户正处于等待输入理由状态。
     *
     * @param openId   操作人飞书 user_id
     * @param chatId   会话 chat_id（私聊可为空）
     * @param decision 决策类型
     * @param todoId   目标待办 ID
     */
    public void mark(String openId, String chatId, Decision decision, String todoId) {
        if (openId == null || openId.isBlank() || todoId == null || todoId.isBlank()) {
            return;
        }
        entries.put(key(openId, chatId),
                new Entry(decision, todoId, System.currentTimeMillis() + ttlMs));
    }

    /**
     * 获取并清除某用户当前等待输入的理由类型；若已过期或不存在返回 {@code null}。
     */
    public PendingReason consume(String openId, String chatId) {
        if (openId == null || openId.isBlank()) {
            return null;
        }
        String k = key(openId, chatId);
        Entry e = entries.remove(k);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expiresAtMs) {
            return null;
        }
        return new PendingReason(e);
    }

    public void clear(String openId, String chatId) {
        if (openId == null) {
            return;
        }
        entries.remove(key(openId, chatId));
    }
}
