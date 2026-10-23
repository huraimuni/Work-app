package kr.todanjeon.workapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    static final String[] TYPES = {"신규 설치", "AS", "영업 방문", "서류 접수", "카드사 서류", "기타"};
    static final String[] STATUS_ACTIONS = {"확인함", "진행중", "보류"};

    private Store store;
    private JSONObject me;
    private LinearLayout main;
    private LinearLayout content;
    private LinearLayout nav;
    private String tab = "task";
    private boolean showAll;
    private String historyQuery = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        if (store.hasUsers()) showLogin();
        else showSetup();
    }

    /** 로그인/첫 설정 공통 화면: 로고 + 큰 문구 + 흰 카드. */
    private LinearLayout authScreen(String headline, String sub, LinearLayout card) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackgroundColor(Ui.BG);
        l.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.logo9);
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(Ui.round(Color.WHITE, 28, this));
        logo.setClipToOutline(true);
        logo.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        logo.setContentDescription("나인페이먼츠 로고");
        logo.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112)));
        l.addView(logo);
        l.addView(Ui.text(this, "나인패밀리", 30, Ui.INK, true), Ui.lp(this, 24));
        l.addView(Ui.text(this, headline + "\n" + sub, 15, Ui.GRAY, false), Ui.lp(this, 8));
        l.addView(card, Ui.lp(this, 32));
        return l;
    }

    // ---------------- 첫 실행: 관리자 만들기 ----------------
    private void showSetup() {
        LinearLayout card = Ui.card(this);
        final EditText name = Ui.field(this, "관리자 이름", false);
        final EditText pin = Ui.field(this, "비밀번호 숫자 4자리 이상", true);
        final EditText pin2 = Ui.field(this, "비밀번호 확인", true);
        card.addView(name);
        card.addView(pin, Ui.lp(this, 10));
        card.addView(pin2, Ui.lp(this, 10));
        Button go = Ui.button(this, "관리자 만들기", Ui.BLUE, Color.WHITE);
        go.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String p = pin.getText().toString();
            if (n.isEmpty()) {
                Ui.toast(this, "이름을 입력해 주세요.");
            } else if (p.length() < 4 || !p.equals(pin2.getText().toString())) {
                Ui.toast(this, "비밀번호는 숫자 4자리 이상이고 두 칸이 같아야 합니다.");
            } else {
                me = store.addUser(n, p, Perm.preset("관리자"));
                buildMain();
            }
        });
        card.addView(go, Ui.lp(this, 16));
        setContentView(authScreen("처음 오셨네요.", "관리자 계정부터 만들어 주세요.\n(이 버전은 이 폰 안에만 저장되는 시험판입니다.)", card));
    }

    private void showLogin() {
        LinearLayout card = Ui.card(this);
        final EditText name = Ui.field(this, "이름", false);
        final EditText pin = Ui.field(this, "비밀번호", true);
        card.addView(name);
        card.addView(pin, Ui.lp(this, 10));
        Button go = Ui.button(this, "로그인", Ui.BLUE, Color.WHITE);
        go.setOnClickListener(v -> {
            JSONObject u = store.login(name.getText().toString().trim(), pin.getText().toString());
            if (u == null) {
                Ui.toast(this, "이름 또는 비밀번호가 맞지 않거나 사용이 중지된 계정입니다.");
            } else {
                me = u;
                buildMain();
            }
        });
        card.addView(go, Ui.lp(this, 16));
        setContentView(authScreen("오늘 업무, 바로 확인하세요.", "이름과 비밀번호로 로그인", card));
    }

    // ---------------- 메인 틀 ----------------
    private boolean can(String perm) {
        return Store.has(me, perm);
    }

    private int myId() {
        return me.optInt("id");
    }

    private TextView fab;

    private void buildMain() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(0, Ui.dp(this, 48), 0, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 12));
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.addView(Ui.text(this, new SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(new Date()), 13, Ui.GRAY, false));
        hl.addView(Ui.text(this, me.optString("name") + "님, 안녕하세요", 22, Ui.INK, true), Ui.lp(this, 2));
        head.addView(hl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView av = Ui.circle(this, me.optString("name"), 44, Color.WHITE, Ui.BLUE);
        av.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("로그아웃")
                .setMessage("로그아웃 할까요?").setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (d, w) -> {
                    me = null;
                    showLogin();
                }).show());
        head.addView(av);
        main.addView(head);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 150));
        sv.addView(content);
        main.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fab = Ui.text(this, "+", 28, Color.WHITE, false);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Ui.round(Ui.NAVY, 28, this));
        fab.setElevation(Ui.dp(this, 6));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        fp.gravity = Gravity.BOTTOM | Gravity.END;
        fp.rightMargin = Ui.dp(this, 20);
        fp.bottomMargin = Ui.dp(this, 104);
        root.addView(fab, fp);

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackground(Ui.round(Color.WHITE, 24, this));
        nav.setElevation(Ui.dp(this, 8));
        nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64));
        np.gravity = Gravity.BOTTOM;
        np.leftMargin = Ui.dp(this, 16);
        np.rightMargin = Ui.dp(this, 16);
        np.bottomMargin = Ui.dp(this, 20);
        root.addView(nav, np);
        setContentView(root);
        render();
    }

    private int newCount() {
        int n = 0;
        for (JSONObject t : store.list(store.tasks())) {
            if (t.optInt("assignee") == myId() && "지시됨".equals(t.optString("status"))) n++;
        }
        return n;
    }

    private int unreadNotices() {
        int n = 0;
        for (JSONObject x : store.list(store.notices())) if (!store.isRead(x, myId())) n++;
        return n;
    }

    private void render() {
        nav.removeAllViews();
        addNav("task", "업무", newCount());
        addNav("notice", "공지", unreadNotices());
        addNav("history", "이력", 0);
        if (can(Perm.STAFF_MANAGE)) addNav("staff", "직원", 0);
        content.removeAllViews();
        if ("task".equals(tab)) renderTasks();
        else if ("notice".equals(tab)) renderNotices();
        else if ("history".equals(tab)) renderHistory();
        else renderStaff();
        updateFab();
    }

    private void updateFab() {
        if ("task".equals(tab) && can(Perm.TASK_CREATE)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("업무 지시");
            fab.setOnClickListener(v -> showCreateTask());
        } else if ("notice".equals(tab) && can(Perm.NOTICE_WRITE)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("공지 작성");
            fab.setOnClickListener(v -> showCreateNotice());
        } else if ("staff".equals(tab)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("직원 추가");
            fab.setOnClickListener(v -> showAddStaff());
        } else {
            fab.setVisibility(View.GONE);
        }
    }

    private void addNav(final String key, String label, int badge) {
        boolean on = key.equals(tab);
        TextView t = Ui.text(this, label + (badge > 0 ? "  " + badge : ""), 14, on ? Ui.BLUE : Ui.GRAY, on);
        t.setGravity(Gravity.CENTER);
        if (on) t.setBackground(Ui.round(Ui.TINT, 16, this));
        t.setOnClickListener(v -> {
            tab = key;
            render();
        });
        nav.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private int statusColor(String s) {
        switch (s) {
            case "지시됨":
                return Ui.AMBER;
            case "확인함":
                return Color.rgb(74, 79, 94);
            case "진행중":
                return Ui.BLUE;
            case "보류":
                return Ui.RED;
            default:
                return Ui.GREEN;
        }
    }

    private int statusBg(String s) {
        switch (s) {
            case "지시됨":
                return Color.rgb(255, 241, 219);
            case "확인함":
                return Color.rgb(238, 240, 244);
            case "진행중":
                return Ui.TINT;
            case "보류":
                return Color.rgb(255, 233, 229);
            default:
                return Color.rgb(227, 246, 238);
        }
    }

    // ---------------- 업무 ----------------
    private void renderTasks() {
        int open = 0, doing = 0, fresh = 0, done = 0;
        String today = Store.now().substring(0, 10);
        for (JSONObject t : store.list(store.tasks())) {
            if (t.optInt("assignee") != myId()) continue;
            String st = t.optString("status");
            if ("완료".equals(st)) {
                if (t.optString("doneAt").startsWith(today)) done++;
            } else {
                open++;
                if ("진행중".equals(st)) doing++;
                if ("지시됨".equals(st)) fresh++;
            }
        }
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.round(Ui.BLUE, 24, this));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.BOTTOM);
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.addView(Ui.text(this, "남은 업무", 14, Color.argb(220, 255, 255, 255), false));
        hl.addView(Ui.text(this, open + "건", 34, Color.WHITE, true));
        hr.addView(hl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hr.addView(Ui.text(this, "새 업무 " + fresh + " · 진행 " + doing + " · 오늘 완료 " + done, 13, Color.argb(220, 255, 255, 255), false));
        hero.addView(hr);
        int total = open + done;
        View bar = new View(this);
        bar.setBackground(Ui.round(Color.argb(70, 255, 255, 255), 4, this));
        FrameLayout track = new FrameLayout(this);
        track.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)));
        View fill = new View(this);
        fill.setBackground(Ui.round(Color.WHITE, 4, this));
        final float ratio = total == 0 ? 0f : (float) done / total;
        final FrameLayout trackF = track;
        final View fillF = fill;
        track.addView(fill, new FrameLayout.LayoutParams(0, Ui.dp(this, 8)));
        track.post(() -> {
            FrameLayout.LayoutParams lp2 = (FrameLayout.LayoutParams) fillF.getLayoutParams();
            lp2.width = Math.round(trackF.getWidth() * ratio);
            fillF.setLayoutParams(lp2);
        });
        hero.addView(track, Ui.lp(this, 16));
        content.addView(hero);

        if (can(Perm.TASK_VIEW_ALL)) {
            LinearLayout row = new LinearLayout(this);
            row.addView(pill("내 업무", !showAll, () -> {
                showAll = false;
                render();
            }));
            row.addView(pill("전체 업무", showAll, () -> {
                showAll = true;
                render();
            }));
            content.addView(row, Ui.lp(this, 16));
        }
        List<JSONObject> list = new ArrayList<>();
        for (JSONObject t : store.list(store.tasks())) {
            if ("완료".equals(t.optString("status"))) continue;
            if (showAll && can(Perm.TASK_VIEW_ALL) || t.optInt("assignee") == myId()) list.add(t);
        }
        Collections.sort(list, (a, b) -> b.optString("created").compareTo(a.optString("created")));
        if (list.isEmpty()) {
            content.addView(Ui.text(this, "진행 중인 업무가 없습니다.", 15, Ui.GRAY, false), Ui.lp(this, 28));
        }
        for (final JSONObject t : list) content.addView(taskCard(t), Ui.lp(this, 10));
    }

    private TextView pill(String label, boolean on, final Runnable r) {
        TextView t = Ui.text(this, label, 14, on ? Color.WHITE : Ui.GRAY, on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.round(on ? Ui.NAVY : Color.WHITE, 18, this));
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 9), Ui.dp(this, 16), Ui.dp(this, 9));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(p);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private View taskCard(final JSONObject t) {
        LinearLayout c = Ui.cardH(this);
        c.addView(Ui.typeTile(this, t.optString("type")));
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        mid.addView(Ui.text(this, t.optString("title"), 16, Ui.INK, true));
        String sub = t.optString("client");
        mid.addView(Ui.text(this, (sub.isEmpty() ? "" : sub + " · ") + store.userName(t.optInt("assignee"))
                + (t.optString("due").isEmpty() ? "" : " · " + t.optString("due")), 13, Ui.GRAY, false), Ui.lp(this, 2));
        c.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String st = t.optString("status");
        c.addView(Ui.chip(this, st, statusBg(st), statusColor(st)));
        c.setOnClickListener(v -> showTask(t.optInt("id")));
        return c;
    }

    private void showCreateTask() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final EditText title = Ui.field(this, "업무 제목 (필수)", false);
        final Spinner type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TYPES));
        final EditText client = Ui.field(this, "거래처(상호)", false);
        final EditText phone = Ui.field(this, "매장 전화", false);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        final EditText addr = Ui.field(this, "매장 주소", false);
        final EditText due = Ui.field(this, "기한 (예: 2026-11-05)", false);
        final EditText memo = Ui.field(this, "메모", false);
        memo.setSingleLine(false);
        final List<JSONObject> staff = new ArrayList<>();
        for (JSONObject u : store.list(store.users())) {
            if (!u.optBoolean("active", true)) continue;
            if (can(Perm.TASK_ASSIGN) || u.optInt("id") == myId()) staff.add(u);
        }
        List<String> names = new ArrayList<>();
        for (JSONObject u : staff) names.add(u.optString("name"));
        final Spinner who = new Spinner(this);
        who.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        f.addView(title);
        f.addView(type);
        f.addView(client);
        f.addView(phone);
        f.addView(addr);
        f.addView(due);
        f.addView(memo);
        f.addView(Ui.text(this, "담당 직원", 13, Ui.GRAY, false), Ui.lp(this, 8));
        f.addView(who);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        new AlertDialog.Builder(this).setTitle("업무 지시").setView(sv)
                .setNegativeButton("취소", null)
                .setPositiveButton("지시하기", (d, w) -> {
                    String ti = title.getText().toString().trim();
                    if (ti.isEmpty() || staff.isEmpty()) {
                        Ui.toast(this, "업무 제목을 입력해 주세요.");
                        return;
                    }
                    int assignee = staff.get(who.getSelectedItemPosition()).optInt("id");
                    store.addTask(ti, TYPES[type.getSelectedItemPosition()], client.getText().toString().trim(),
                            phone.getText().toString().trim(), addr.getText().toString().trim(),
                            due.getText().toString().trim(), memo.getText().toString().trim(), assignee, myId());
                    Ui.toast(this, store.userName(assignee) + "님에게 업무를 지시했습니다.");
                    render();
                }).show();
    }

    private void showTask(final int id) {
        final JSONObject t = store.task(id);
        if (t == null) return;
        final LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        l.addView(Ui.text(this, t.optString("type") + "  ·  " + t.optString("status"), 14, statusColor(t.optString("status")), true));
        addLine(l, "거래처", t.optString("client"));
        addLine(l, "전화", t.optString("phone"));
        addLine(l, "주소", t.optString("addr"));
        addLine(l, "기한", t.optString("due"));
        addLine(l, "담당", store.userName(t.optInt("assignee")));
        addLine(l, "지시한 사람", store.userName(t.optInt("creator")));
        addLine(l, "메모", t.optString("memo"));
        if (t.has("doneMsg")) addLine(l, "완료 메시지", t.optString("doneMsg"));
        l.addView(Ui.text(this, "처리 이력", 14, Ui.NAVY, true), Ui.lp(this, 12));
        JSONArray h = t.optJSONArray("history");
        for (int i = 0; h != null && i < h.length(); i++) {
            JSONObject e = h.optJSONObject(i);
            l.addView(Ui.text(this, e.optString("t") + "  " + store.userName(e.optInt("who")) + "\n" + e.optString("text"), 12, Ui.GRAY, false), Ui.lp(this, 4));
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(t.optString("title"));
        final AlertDialog[] dlg = new AlertDialog[1];
        final String phone = t.optString("phone");
        final String addr = t.optString("addr");
        LinearLayout acts = new LinearLayout(this);
        if (!phone.isEmpty()) {
            Button call = Ui.button(this, "전화", Ui.NAVY, Color.WHITE);
            call.setOnClickListener(v -> openIntent(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))));
            acts.addView(call, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (!addr.isEmpty()) {
            Button map = Ui.button(this, "길찾기", Ui.NAVY, Color.WHITE);
            map.setOnClickListener(v -> {
                try {
                    openIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + URLEncoder.encode(addr, "UTF-8"))));
                } catch (Exception ignored) {
                }
            });
            acts.addView(map, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (acts.getChildCount() > 0) l.addView(acts, Ui.lp(this, 10));
        boolean mineOrAdmin = t.optInt("assignee") == myId() || can(Perm.STAFF_MANAGE);
        if (mineOrAdmin && !"완료".equals(t.optString("status"))) {
            LinearLayout row = new LinearLayout(this);
            for (final String s : STATUS_ACTIONS) {
                Button sb = Ui.button(this, s, Ui.BLUE, Color.WHITE);
                sb.setOnClickListener(v -> {
                    store.setStatus(t, myId(), s, "");
                    if (dlg[0] != null) dlg[0].dismiss();
                    render();
                    showTask(id);
                });
                row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            l.addView(row, Ui.lp(this, 8));
            Button done = Ui.button(this, "처리 완료", Ui.GREEN, Color.WHITE);
            done.setOnClickListener(v -> {
                if (dlg[0] != null) dlg[0].dismiss();
                askComplete(t);
            });
            l.addView(done, Ui.lp(this, 8));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        b.setView(sv).setPositiveButton("닫기", null);
        dlg[0] = b.show();
        // 지시됨 상태에서 담당자가 열면 자동으로 확인함 처리
        if ("지시됨".equals(t.optString("status")) && t.optInt("assignee") == myId()) {
            store.setStatus(t, myId(), "확인함", "");
            render();
        }
    }

    private void askComplete(final JSONObject t) {
        final EditText msg = Ui.field(this, "처리 완료 메시지 (필수)", false);
        msg.setSingleLine(false);
        FrameLayoutWrap w = new FrameLayoutWrap(this, msg);
        new AlertDialog.Builder(this).setTitle("처리 완료").setView(w)
                .setNegativeButton("취소", null)
                .setPositiveButton("완료 처리", (d, x) -> {
                    String m = msg.getText().toString().trim();
                    if (m.isEmpty()) {
                        Ui.toast(this, "완료 메시지를 입력해 주세요.");
                        return;
                    }
                    store.setStatus(t, myId(), "완료", m);
                    Ui.toast(this, "완료 처리했습니다. 이력에 보관됩니다.");
                    render();
                }).show();
    }

    /** 다이얼로그 안 여백용. */
    static final class FrameLayoutWrap extends LinearLayout {
        FrameLayoutWrap(android.content.Context c, View v) {
            super(c);
            setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), 0);
            addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void addLine(LinearLayout l, String k, String v) {
        if (v == null || v.isEmpty()) return;
        l.addView(Ui.text(this, k + "  " + v, 14, Ui.INK, false), Ui.lp(this, 4));
    }

    private void openIntent(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, "이 폰에서 열 수 있는 앱이 없습니다.");
        }
    }

    // ---------------- 공지 ----------------
    private void renderNotices() {
        List<JSONObject> list = store.list(store.notices());
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                int pa = a.optBoolean("pinned") ? 1 : 0, pb = b.optBoolean("pinned") ? 1 : 0;
                if (pa != pb) return pb - pa;
                return b.optString("t").compareTo(a.optString("t"));
            }
        });
        if (list.isEmpty()) content.addView(Ui.text(this, "공지가 없습니다.", 15, Ui.GRAY, false), Ui.lp(this, 24));
        for (final JSONObject n : list) {
            LinearLayout c = Ui.card(this);
            boolean read = store.isRead(n, myId());
            c.addView(Ui.text(this, (n.optBoolean("pinned") ? "[고정] " : "") + (read ? "" : "● ") + n.optString("title"), 16, Ui.INK, !read));
            c.addView(Ui.text(this, n.optString("t") + "  ·  " + store.userName(n.optInt("author")), 12, Ui.GRAY, false), Ui.lp(this, 2));
            c.setOnClickListener(v -> showNotice(n));
            content.addView(c, Ui.lp(this, 10));
        }
    }

    private void showNotice(JSONObject n) {
        store.markRead(n, myId());
        StringBuilder sb = new StringBuilder(n.optString("body"));
        if (can(Perm.NOTICE_WRITE) || can(Perm.STAFF_MANAGE)) {
            JSONArray r = n.optJSONArray("readBy");
            sb.append("\n\n읽은 사람 ").append(r == null ? 0 : r.length()).append("명: ");
            for (int i = 0; r != null && i < r.length(); i++) sb.append(i > 0 ? ", " : "").append(store.userName(r.optInt(i)));
        }
        new AlertDialog.Builder(this).setTitle(n.optString("title")).setMessage(sb.toString())
                .setPositiveButton("닫기", null).show();
        render();
    }

    private void showCreateNotice() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final EditText title = Ui.field(this, "제목", false);
        final EditText body = Ui.field(this, "내용", false);
        body.setSingleLine(false);
        body.setMinLines(4);
        final CheckBox pin = new CheckBox(this);
        pin.setText("맨 위에 고정");
        f.addView(title);
        f.addView(body);
        f.addView(pin);
        new AlertDialog.Builder(this).setTitle("공지 작성").setView(f)
                .setNegativeButton("취소", null)
                .setPositiveButton("등록", (d, w) -> {
                    if (title.getText().toString().trim().isEmpty()) {
                        Ui.toast(this, "제목을 입력해 주세요.");
                        return;
                    }
                    store.addNotice(title.getText().toString().trim(), body.getText().toString().trim(), pin.isChecked(), myId());
                    render();
                }).show();
    }

    // ---------------- 이력 ----------------
    private void renderHistory() {
        final EditText q = Ui.field(this, "검색 (제목, 거래처, 담당자, 완료 메시지)", false);
        q.setBackground(Ui.round(Color.WHITE, 16, this));
        q.setText(historyQuery);
        content.addView(q, Ui.lp(this, 0));
        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(listBox);
        fillHistory(listBox);
        q.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                historyQuery = s.toString();
                fillHistory(listBox);
            }
        });
    }

    private void fillHistory(LinearLayout box) {
        box.removeAllViews();
        String key = historyQuery.trim().toLowerCase();
        List<JSONObject> list = new ArrayList<>();
        for (JSONObject t : store.list(store.tasks())) {
            if (!"완료".equals(t.optString("status"))) continue;
            if (!can(Perm.HISTORY_ALL) && t.optInt("assignee") != myId()) continue;
            String hay = (t.optString("title") + t.optString("client") + store.userName(t.optInt("assignee")) + t.optString("doneMsg")).toLowerCase();
            if (key.isEmpty() || hay.contains(key)) list.add(t);
        }
        Collections.sort(list, (a, b) -> b.optString("doneAt").compareTo(a.optString("doneAt")));
        if (list.isEmpty()) box.addView(Ui.text(this, "완료된 업무 이력이 없습니다.", 15, Ui.GRAY, false), Ui.lp(this, 24));
        for (final JSONObject t : list) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, "[" + t.optString("type") + "] " + t.optString("title"), 16, Ui.INK, true));
            c.addView(Ui.text(this, t.optString("client") + "  ·  " + store.userName(t.optInt("assignee")) + "  ·  완료 " + t.optString("doneAt"), 12, Ui.GRAY, false), Ui.lp(this, 2));
            c.addView(Ui.text(this, t.optString("doneMsg"), 13, Ui.GREEN, false), Ui.lp(this, 4));
            c.setOnClickListener(v -> showTask(t.optInt("id")));
            box.addView(c, Ui.lp(this, 10));
        }
    }

    // ---------------- 직원/권한 ----------------
    private void renderStaff() {
        for (final JSONObject u : store.list(store.users())) {
            LinearLayout c = Ui.card(this);
            boolean active = u.optBoolean("active", true);
            c.addView(Ui.text(this, u.optString("name") + (active ? "" : "  (사용 중지)"), 16, active ? Ui.INK : Ui.GRAY, true));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Perm.KEYS.length; i++) if (Store.has(u, Perm.KEYS[i])) sb.append(sb.length() > 0 ? ", " : "").append(Perm.LABELS[i]);
            c.addView(Ui.text(this, sb.length() == 0 ? "기본 권한(내 업무만)" : sb.toString(), 12, Ui.GRAY, false), Ui.lp(this, 2));
            c.setOnClickListener(v -> showEditStaff(u));
            content.addView(c, Ui.lp(this, 10));
        }
    }

    private void showAddStaff() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final EditText name = Ui.field(this, "직원 이름", false);
        final EditText pin = Ui.field(this, "처음 비밀번호 (숫자 4자리 이상)", true);
        final Spinner preset = new Spinner(this);
        preset.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Perm.PRESET_NAMES));
        f.addView(name);
        f.addView(pin);
        f.addView(Ui.text(this, "권한 묶음 (추가 후 항목별로 바꿀 수 있습니다)", 13, Ui.GRAY, false), Ui.lp(this, 8));
        f.addView(preset);
        new AlertDialog.Builder(this).setTitle("직원 추가").setView(f)
                .setNegativeButton("취소", null)
                .setPositiveButton("추가", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String p = pin.getText().toString();
                    if (n.isEmpty() || p.length() < 4) {
                        Ui.toast(this, "이름과 4자리 이상 비밀번호를 입력해 주세요.");
                    } else if (store.nameExists(n)) {
                        Ui.toast(this, "같은 이름의 직원이 있습니다.");
                    } else {
                        store.addUser(n, p, Perm.preset(Perm.PRESET_NAMES[preset.getSelectedItemPosition()]));
                        render();
                    }
                }).show();
    }

    private void showEditStaff(final JSONObject u) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final CheckBox[] boxes = new CheckBox[Perm.KEYS.length];
        for (int i = 0; i < Perm.KEYS.length; i++) {
            boxes[i] = new CheckBox(this);
            boxes[i].setText(Perm.LABELS[i]);
            boxes[i].setChecked(Store.has(u, Perm.KEYS[i]));
            f.addView(boxes[i]);
        }
        final EditText pin = Ui.field(this, "비밀번호 재설정 (바꿀 때만 입력)", true);
        f.addView(pin, Ui.lp(this, 8));
        final boolean active = u.optBoolean("active", true);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        new AlertDialog.Builder(this).setTitle(u.optString("name") + " 권한").setView(sv)
                .setNegativeButton("닫기", null)
                .setNeutralButton(active ? "사용 중지" : "다시 사용", (d, w) -> {
                    boolean isAdmin = Store.has(u, Perm.STAFF_MANAGE);
                    if (active && isAdmin && store.adminCount() <= 1) {
                        Ui.toast(this, "마지막 관리자는 중지할 수 없습니다.");
                        return;
                    }
                    try {
                        u.put("active", !active);
                    } catch (Exception ignored) {
                    }
                    store.save();
                    render();
                })
                .setPositiveButton("저장", (d, w) -> {
                    JSONArray np = new JSONArray();
                    for (int i = 0; i < Perm.KEYS.length; i++) if (boxes[i].isChecked()) np.put(Perm.KEYS[i]);
                    boolean wasAdmin = Store.has(u, Perm.STAFF_MANAGE);
                    boolean willAdmin = boxes[Perm.KEYS.length - 1].isChecked();
                    if (wasAdmin && !willAdmin && store.adminCount() <= 1) {
                        Ui.toast(this, "마지막 관리자의 직원 관리 권한은 끌 수 없습니다.");
                        return;
                    }
                    try {
                        u.put("perms", np);
                        if (pin.getText().length() >= 4) u.put("pin", Store.hash(pin.getText().toString()));
                    } catch (Exception ignored) {
                    }
                    store.save();
                    if (u.optInt("id") == myId()) me = u;
                    render();
                }).show();
    }
}
