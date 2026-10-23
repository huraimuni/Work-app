package kr.todanjeon.workapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v0.1 로컬 저장소. 이 폰 안에만 저장된다(데모용). 서버(Firebase) 연결 시 이 클래스만 교체한다.
 * 업무 이력은 삭제하지 않고 계속 보관한다.
 */
final class Store {
    private final SharedPreferences sp;
    private JSONObject db;

    Store(Context c) {
        sp = c.getSharedPreferences("workapp", Context.MODE_PRIVATE);
        try {
            db = new JSONObject(sp.getString("db", "{}"));
        } catch (JSONException e) {
            db = new JSONObject();
        }
        try {
            if (!db.has("users")) db.put("users", new JSONArray());
            if (!db.has("tasks")) db.put("tasks", new JSONArray());
            if (!db.has("notices")) db.put("notices", new JSONArray());
            if (!db.has("projects")) db.put("projects", new JSONArray());
            if (!db.has("contracts")) db.put("contracts", new JSONArray());
            if (!db.has("events")) db.put("events", new JSONArray());
            if (!db.has("seq")) db.put("seq", 0);
        } catch (JSONException ignored) {
        }
    }

    void save() {
        sp.edit().putString("db", db.toString()).apply();
    }

    static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(new Date());
    }

    int nextId() {
        int n = db.optInt("seq") + 1;
        try {
            db.put("seq", n);
        } catch (JSONException ignored) {
        }
        return n;
    }

    static String hash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("workapp:" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }

    // ---------- 로그인 유지 ----------
    void setSession(int id) {
        sp.edit().putInt("session", id).apply();
    }

    JSONObject sessionUser() {
        int id = sp.getInt("session", 0);
        JSONObject u = id == 0 ? null : user(id);
        return u != null && u.optBoolean("active", true) ? u : null;
    }

    // ---------- 직원 ----------
    boolean hasUsers() {
        return db.optJSONArray("users").length() > 0;
    }

    JSONArray users() {
        return db.optJSONArray("users");
    }

    JSONObject user(int id) {
        JSONArray a = users();
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optInt("id") == id) return a.optJSONObject(i);
        return null;
    }

    String userName(int id) {
        JSONObject u = user(id);
        return u == null ? "(알 수 없음)" : u.optString("name");
    }

    JSONObject login(String name, String pin) {
        JSONArray a = users();
        for (int i = 0; i < a.length(); i++) {
            JSONObject u = a.optJSONObject(i);
            if (u.optString("name").equals(name) && u.optString("pin").equals(hash(pin)) && u.optBoolean("active", true)) {
                return u;
            }
        }
        return null;
    }

    boolean nameExists(String name) {
        JSONArray a = users();
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optString("name").equals(name)) return true;
        return false;
    }

    JSONObject addUser(String name, String pin, List<String> perms) {
        try {
            JSONObject u = new JSONObject();
            u.put("id", nextId());
            u.put("name", name);
            u.put("pin", hash(pin));
            u.put("active", true);
            u.put("perms", new JSONArray(perms));
            users().put(u);
            save();
            return u;
        } catch (JSONException e) {
            return null;
        }
    }

    static boolean has(JSONObject user, String perm) {
        JSONArray p = user.optJSONArray("perms");
        if (p == null) return false;
        for (int i = 0; i < p.length(); i++) if (perm.equals(p.optString(i))) return true;
        return false;
    }

    int adminCount() {
        int n = 0;
        JSONArray a = users();
        for (int i = 0; i < a.length(); i++) {
            JSONObject u = a.optJSONObject(i);
            if (u.optBoolean("active", true) && has(u, Perm.STAFF_MANAGE)) n++;
        }
        return n;
    }

    // ---------- 업무 ----------
    JSONArray tasks() {
        return db.optJSONArray("tasks");
    }

    JSONObject task(int id) {
        JSONArray a = tasks();
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optInt("id") == id) return a.optJSONObject(i);
        return null;
    }

    JSONObject addTask(String title, String type, String client, String phone, String addr, String due,
                       String memo, int assignee, int creator) {
        try {
            JSONObject t = new JSONObject();
            t.put("id", nextId());
            t.put("title", title);
            t.put("type", type);
            t.put("client", client);
            t.put("phone", phone);
            t.put("addr", addr);
            t.put("due", due);
            t.put("memo", memo);
            t.put("assignee", assignee);
            t.put("creator", creator);
            t.put("status", "지시됨");
            t.put("created", now());
            t.put("history", new JSONArray());
            log(t, creator, "업무 지시 → " + userName(assignee));
            tasks().put(t);
            save();
            return t;
        } catch (JSONException e) {
            return null;
        }
    }

    void log(JSONObject t, int who, String text) {
        try {
            JSONObject h = new JSONObject();
            h.put("t", now());
            h.put("who", who);
            h.put("text", text);
            t.getJSONArray("history").put(h);
        } catch (JSONException ignored) {
        }
    }

    void setStatus(JSONObject t, int who, String status, String msg) {
        try {
            t.put("status", status);
            if ("완료".equals(status)) {
                t.put("doneMsg", msg);
                t.put("doneAt", now());
            }
            log(t, who, "상태 → " + status + (msg == null || msg.isEmpty() ? "" : " : " + msg));
            save();
        } catch (JSONException ignored) {
        }
    }

    // ---------- 공지 ----------
    JSONArray notices() {
        return db.optJSONArray("notices");
    }

    void addNotice(String title, String body, boolean pinned, int author) {
        try {
            JSONObject n = new JSONObject();
            n.put("id", nextId());
            n.put("title", title);
            n.put("body", body);
            n.put("pinned", pinned);
            n.put("author", author);
            n.put("t", now());
            n.put("readBy", new JSONArray());
            notices().put(n);
            save();
        } catch (JSONException ignored) {
        }
    }

    boolean isRead(JSONObject n, int uid) {
        JSONArray r = n.optJSONArray("readBy");
        for (int i = 0; r != null && i < r.length(); i++) if (r.optInt(i) == uid) return true;
        return false;
    }

    void markRead(JSONObject n, int uid) {
        if (isRead(n, uid)) return;
        n.optJSONArray("readBy").put(uid);
        save();
    }

    // ---------- 프로젝트 ----------
    JSONArray projects() {
        return db.optJSONArray("projects");
    }

    void addProject(String title, String due, String memo, int owner, int creator) {
        try {
            JSONObject p = new JSONObject();
            p.put("id", nextId());
            p.put("title", title);
            p.put("due", due);
            p.put("memo", memo);
            p.put("owner", owner);
            p.put("pct", 0);
            p.put("check", false);
            p.put("created", now());
            p.put("notes", new JSONArray());
            projects().put(p);
            save();
        } catch (JSONException ignored) {
        }
    }

    void updateProject(JSONObject p, int who, int pct, boolean check, String memo) {
        try {
            p.put("pct", pct);
            p.put("check", check);
            p.put("memo", memo);
            JSONObject h = new JSONObject();
            h.put("t", now());
            h.put("who", who);
            h.put("text", pct + "% · " + (check ? "점검 필요 · " : "") + memo);
            p.getJSONArray("notes").put(h);
            save();
        } catch (JSONException ignored) {
        }
    }

    // ---------- 신규 계약 ----------
    JSONArray contracts() {
        return db.optJSONArray("contracts");
    }

    void addContract(String date, String client, String product, String linkType, String company,
                     String kind, int qty, int who) {
        try {
            JSONObject c = new JSONObject();
            c.put("id", nextId());
            c.put("date", date);
            c.put("client", client);
            c.put("product", product);
            c.put("linkType", linkType);
            c.put("company", company);
            c.put("kind", kind);
            c.put("qty", qty);
            c.put("who", who);
            c.put("created", now());
            contracts().put(c);
            save();
        } catch (JSONException ignored) {
        }
    }

    // ---------- 일정 ----------
    JSONArray events() {
        return db.optJSONArray("events");
    }

    void addEvent(String date, String title, int who) {
        try {
            JSONObject e = new JSONObject();
            e.put("id", nextId());
            e.put("date", date);
            e.put("title", title);
            e.put("who", who);
            events().put(e);
            save();
        } catch (JSONException ignored) {
        }
    }

    List<JSONObject> list(JSONArray a) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(a.optJSONObject(i));
        return out;
    }
}
