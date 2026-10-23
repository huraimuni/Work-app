package kr.todanjeon.workapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.SeekBar;
import java.util.Calendar;
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
    private String sub = null;
    private int calY = 0, calM = 0;
    private String calSel = "";
    private int perfY = 0, perfM = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        showSplash();
        new Handler().postDelayed(() -> {
            if (store.hasUsers()) showLogin();
            else showSetup();
        }, 1800);
    }

    /** 앱을 켜면 잠깐 보이는 시작 화면. */
    private void showSplash() {
        FrameLayout f = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(251, 246, 232), Color.rgb(240, 225, 176), Color.rgb(227, 200, 119)});
        f.setBackground(g);
        View w1 = new View(this);
        w1.setBackground(Ui.round(Color.argb(90, 205, 170, 82), 120, this));
        FrameLayout.LayoutParams a1 = new FrameLayout.LayoutParams(Ui.dp(this, 520), Ui.dp(this, 260));
        a1.gravity = Gravity.BOTTOM | Gravity.START;
        a1.bottomMargin = -Ui.dp(this, 120);
        a1.leftMargin = -Ui.dp(this, 80);
        f.addView(w1, a1);
        View w2 = new View(this);
        w2.setBackground(Ui.round(Color.argb(110, 143, 107, 20), 120, this));
        FrameLayout.LayoutParams a2 = new FrameLayout.LayoutParams(Ui.dp(this, 520), Ui.dp(this, 240));
        a2.gravity = Gravity.BOTTOM | Gravity.END;
        a2.bottomMargin = -Ui.dp(this, 160);
        a2.rightMargin = -Ui.dp(this, 120);
        f.addView(w2, a2);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo9);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(Ui.round(Color.WHITE, 28, this));
        logo.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        logo.setContentDescription("나인페이먼츠 로고");
        col.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112)));
        TextView t = Ui.text(this, "나인패밀리", 30, Ui.DARK, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t, Ui.lp(this, 20));
        TextView s = Ui.text(this, "함께 만드는 성공,\n오늘도 한 걸음 더!", 19, Ui.DARK, true);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.25f);
        col.addView(s, Ui.lp(this, 22));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        cp.bottomMargin = Ui.dp(this, 40);
        f.addView(col, cp);
        setContentView(f);
    }

    /** 로그인/첫 설정 공통 화면: 로고 + 큰 문구 + 흰 카드. */
    private LinearLayout authScreen(String headline, String sub, LinearLayout card) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackgroundColor(Color.WHITE);
        l.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.logo9);
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(Ui.round(Ui.TINT, 22, this));
        logo.setClipToOutline(true);
        logo.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        logo.setContentDescription("나인페이먼츠 로고");
        logo.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 80)));
        l.addView(logo);
        l.addView(Ui.text(this, "나인패밀리", 28, Ui.INK, true), Ui.lp(this, 20));
        l.addView(Ui.text(this, headline + "\n" + sub, 15, Ui.GRAY, false), Ui.lp(this, 8));
        l.addView(card, Ui.lp(this, 32));
        return l;
    }

    // ---------------- 첫 실행: 관리자 만들기 ----------------
    private void showSetup() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        final EditText name = Ui.field(this, "관리자 이름", false);
        final EditText pin = Ui.field(this, "비밀번호 숫자 4자리 이상", true);
        final EditText pin2 = Ui.field(this, "비밀번호 확인", true);
        card.addView(name);
        card.addView(pin, Ui.lp(this, 10));
        card.addView(pin2, Ui.lp(this, 10));
        Button go = Ui.button(this, "관리자 만들기", Ui.GOLD, Ui.DARK);
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        final EditText name = Ui.field(this, "이름", false);
        final EditText pin = Ui.field(this, "비밀번호", true);
        card.addView(name);
        card.addView(pin, Ui.lp(this, 10));
        Button go = Ui.button(this, "로그인", Ui.GOLD, Ui.DARK);
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
        setContentView(authScreen("오늘도 반갑습니다.", "이름과 비밀번호로 로그인 (휴대폰 인증은 서버 연결 후 켜집니다)", card));
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
        root.setBackgroundColor(Color.WHITE);

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
        TextView av = Ui.circle(this, me.optString("name"), 44, Ui.GOLD, Ui.DARK);
        av.setOnClickListener(v -> {
            tab = "all";
            sub = null;
            render();
        });
        head.addView(av);
        main.addView(head);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 160));
        sv.addView(content);
        main.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fab = Ui.text(this, "+", 28, Ui.DARK, false);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Ui.round(Ui.GOLD, 28, this));
        fab.setElevation(Ui.dp(this, 6));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        fp.gravity = Gravity.BOTTOM | Gravity.END;
        fp.rightMargin = Ui.dp(this, 20);
        fp.bottomMargin = Ui.dp(this, 84);
        root.addView(fab, fp);

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(Ui.dp(this, 10));
        nav.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64));
        np.gravity = Gravity.BOTTOM;
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
        addNav("project", "프로젝트", 0);
        addNav("chat", "채팅", 0);
        if (canPerf()) addNav("perf", "실적", 0);
        addNav("all", "전체", unreadNotices());
        content.removeAllViews();
        String k = sub != null ? sub : tab;
        if (sub != null) addBack();
        if ("task".equals(k)) renderTasks();
        else if ("project".equals(k)) renderProjects();
        else if ("chat".equals(k)) renderChat();
        else if ("perf".equals(k)) renderPerf();
        else if ("calendar".equals(k)) renderCalendar();
        else if ("notice".equals(k)) renderNotices();
        else if ("history".equals(k)) renderHistory();
        else if ("staff".equals(k)) renderStaff();
        else renderAll();
        updateFab();
    }

    private boolean canPerf() {
        return can(Perm.PERF_VIEW) || can(Perm.STAFF_MANAGE);
    }

    private void addBack() {
        TextView b = Ui.text(this, "‹  전체", 15, Ui.DEEP, true);
        b.setPadding(0, Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 12));
        b.setOnClickListener(v -> {
            sub = null;
            tab = "all";
            render();
        });
        content.addView(b);
    }

    private void updateFab() {
        String k = sub != null ? sub : tab;
        if ("project".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("프로젝트 만들기");
            fab.setOnClickListener(v -> showAddProject());
        } else if ("perf".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("신규 계약 올리기");
            fab.setOnClickListener(v -> showAddContract());
        } else if ("calendar".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("일정 추가");
            fab.setOnClickListener(v -> showAddEvent());
        } else if ("task".equals(k) && can(Perm.TASK_CREATE)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("업무 지시");
            fab.setOnClickListener(v -> showCreateTask());
        } else if ("notice".equals(k) && can(Perm.NOTICE_WRITE)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("공지 작성");
            fab.setOnClickListener(v -> showCreateNotice());
        } else if ("staff".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("직원 추가");
            fab.setOnClickListener(v -> showAddStaff());
        } else {
            fab.setVisibility(View.GONE);
        }
    }

    private void addNav(final String key, String label, int badge) {
        boolean on = key.equals(tab);
        TextView t = Ui.text(this, label + (badge > 0 ? " " + badge : ""), 14, on ? Ui.DARK : Ui.GRAY, on);
        t.setGravity(Gravity.CENTER);
        if (on) t.setBackground(Ui.round(Ui.TINT, 16, this));
        t.setOnClickListener(v -> {
            tab = key;
            sub = null;
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
                return Ui.DEEP;
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
        hero.setBackground(Ui.round(Ui.GOLD, 24, this));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.BOTTOM);
        LinearLayout hl = new LinearLayout(this);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.addView(Ui.text(this, "남은 업무", 14, Color.argb(200, 31, 27, 18), false));
        hl.addView(Ui.text(this, open + "건", 34, Ui.DARK, true));
        hr.addView(hl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        hr.addView(Ui.text(this, "새 업무 " + fresh + " · 진행 " + doing + " · 오늘 완료 " + done, 13, Color.argb(200, 31, 27, 18), false));
        hero.addView(hr);
        int total = open + done;
        View bar = new View(this);
        bar.setBackground(Ui.round(Color.argb(60, 31, 27, 18), 4, this));
        FrameLayout track = new FrameLayout(this);
        track.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)));
        View fill = new View(this);
        fill.setBackground(Ui.round(Ui.DARK, 4, this));
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
        t.setBackground(Ui.round(on ? Ui.NAVY : Ui.FIELD, 18, this));
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
                Button sb = Ui.button(this, s, Ui.GOLD, Ui.DARK);
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
        q.setBackground(Ui.round(Ui.FIELD, 16, this));
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

    // ================= v0.3 새 화면 =================
    private TextView title(String s) {
        TextView t = Ui.text(this, s, 26, Ui.INK, true);
        t.setPadding(0, 0, 0, Ui.dp(this, 6));
        return t;
    }

    private View bar(int pct, int fillColor, int trackColor, int heightDp) {
        LinearLayout b = new LinearLayout(this);
        b.setBackground(Ui.round(trackColor, 4, this));
        View f = new View(this);
        f.setBackground(Ui.round(fillColor, 4, this));
        int p = Math.max(0, Math.min(100, pct));
        b.addView(f, new LinearLayout.LayoutParams(0, Ui.dp(this, heightDp), p));
        b.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, heightDp), 100 - p));
        return b;
    }

    private List<JSONObject> activeStaff() {
        List<JSONObject> staff = new ArrayList<>();
        for (JSONObject u : store.list(store.users())) if (u.optBoolean("active", true)) staff.add(u);
        return staff;
    }

    private Spinner staffSpinner(List<JSONObject> staff) {
        List<String> names = new ArrayList<>();
        for (JSONObject u : staff) names.add(u.optString("name"));
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        return s;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        return s;
    }

    private LinearLayout form() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        return f;
    }

    // ---------------- 프로젝트 ----------------
    private void renderProjects() {
        content.addView(title("프로젝트"));
        List<JSONObject> l = store.list(store.projects());
        Collections.sort(l, (a, b) -> b.optString("created").compareTo(a.optString("created")));
        if (l.isEmpty()) {
            content.addView(Ui.text(this, "등록된 프로젝트가 없습니다.\n오른쪽 아래 + 버튼으로 만들어 보세요.", 15, Ui.GRAY, false), Ui.lp(this, 24));
        }
        for (final JSONObject p : l) {
            LinearLayout c = Ui.card(this);
            boolean check = p.optBoolean("check");
            if (check) c.setBackground(Ui.round(Color.rgb(255, 249, 238), 20, this));
            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(Ui.text(this, p.optString("title"), 17, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (check) top.addView(Ui.chip(this, "점검 필요", Color.rgb(255, 231, 186), Color.rgb(90, 58, 0)));
            else top.addView(Ui.text(this, p.optInt("pct") + "%", 20, Ui.DEEP, true));
            c.addView(top);
            c.addView(bar(p.optInt("pct"), Ui.GOLD, Color.rgb(239, 233, 216), 8), Ui.lp(this, 12));
            String memo = p.optString("memo");
            if (!memo.isEmpty()) c.addView(Ui.text(this, memo, 13, Color.rgb(74, 79, 94), false), Ui.lp(this, 10));
            c.addView(Ui.text(this, "담당 " + store.userName(p.optInt("owner")) + (p.optString("due").isEmpty() ? "" : "  ·  " + p.optString("due") + "까지"), 12, Ui.GRAY, false), Ui.lp(this, 8));
            c.setOnClickListener(v -> showProject(p));
            content.addView(c, Ui.lp(this, 12));
        }
    }

    private void showAddProject() {
        LinearLayout f = form();
        final EditText ti = Ui.field(this, "프로젝트 이름 (필수)", false);
        final EditText due = Ui.field(this, "마감일 (예: 2026-12-31)", false);
        final EditText memo = Ui.field(this, "설명", false);
        memo.setSingleLine(false);
        final List<JSONObject> staff = activeStaff();
        final Spinner who = staffSpinner(staff);
        f.addView(ti);
        f.addView(due);
        f.addView(memo);
        f.addView(Ui.text(this, "담당 직원", 13, Ui.GRAY, false), Ui.lp(this, 8));
        f.addView(who);
        new AlertDialog.Builder(this).setTitle("새 프로젝트").setView(f)
                .setNegativeButton("취소", null)
                .setPositiveButton("만들기", (d, w) -> {
                    if (ti.getText().toString().trim().isEmpty() || staff.isEmpty()) {
                        Ui.toast(this, "프로젝트 이름을 입력해 주세요.");
                        return;
                    }
                    store.addProject(ti.getText().toString().trim(), due.getText().toString().trim(),
                            memo.getText().toString().trim(), staff.get(who.getSelectedItemPosition()).optInt("id"), myId());
                    render();
                }).show();
    }

    private void showProject(final JSONObject p) {
        LinearLayout f = form();
        final TextView pctText = Ui.text(this, p.optInt("pct") + "%", 22, Ui.DEEP, true);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(10);
        sb.setProgress(p.optInt("pct") / 10);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                pctText.setText((v * 10) + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        final EditText memo = Ui.field(this, "진행 상황 메모", false);
        memo.setSingleLine(false);
        memo.setText(p.optString("memo"));
        final CheckBox chk = new CheckBox(this);
        chk.setText("점검이 필요해요");
        chk.setChecked(p.optBoolean("check"));
        f.addView(pctText);
        f.addView(sb);
        f.addView(memo);
        f.addView(chk, Ui.lp(this, 8));
        JSONArray notes = p.optJSONArray("notes");
        if (notes != null && notes.length() > 0) {
            f.addView(Ui.text(this, "변경 기록", 13, Ui.NAVY, true), Ui.lp(this, 12));
            for (int i = notes.length() - 1; i >= 0 && i > notes.length() - 6; i--) {
                JSONObject e = notes.optJSONObject(i);
                f.addView(Ui.text(this, e.optString("t") + "  " + store.userName(e.optInt("who")) + "\n" + e.optString("text"), 12, Ui.GRAY, false), Ui.lp(this, 4));
            }
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        new AlertDialog.Builder(this).setTitle(p.optString("title")).setView(sv)
                .setNegativeButton("닫기", null)
                .setPositiveButton("저장", (d, w) -> {
                    store.updateProject(p, myId(), sb.getProgress() * 10, chk.isChecked(), memo.getText().toString().trim());
                    render();
                }).show();
    }

    // ---------------- 채팅 ----------------
    private void renderChat() {
        content.addView(title("채팅"));
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "채팅은 서버 연결 후 열려요", 17, Ui.INK, true));
        c.addView(Ui.text(this, "업무를 지시하거나 서류·입금을 올리면 해당 채팅방에 카드로 자동 정리되고, 직원 전체 채팅방도 함께 만들어집니다. 이 시험판은 폰 안에만 저장되어 채팅은 아직 쓸 수 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 8));
        content.addView(c, Ui.lp(this, 12));
        String[] rooms = {"전체방", "서류접수", "입금확인", "신규계약", "AS·설치", "공지"};
        for (String r : rooms) {
            LinearLayout row = Ui.cardH(this);
            row.addView(Ui.tile(this, r.substring(0, 1), Ui.TINT, Ui.DEEP));
            TextView t = Ui.text(this, r, 16, Ui.INK, true);
            t.setPadding(Ui.dp(this, 14), 0, 0, 0);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.chip(this, "준비 중", Ui.FIELD, Ui.GRAY));
            row.setOnClickListener(v -> Ui.toast(this, "서버 연결 후 사용할 수 있어요."));
            content.addView(row, Ui.lp(this, 8));
        }
    }

    // ---------------- 실적 ----------------
    private void renderPerf() {
        Calendar now = Calendar.getInstance();
        if (perfY == 0) {
            perfY = now.get(Calendar.YEAR);
            perfM = now.get(Calendar.MONTH) + 1;
        }
        content.addView(title("실적"));
        LinearLayout nav2 = new LinearLayout(this);
        nav2.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = Ui.text(this, "‹", 24, Ui.DEEP, true);
        prev.setPadding(0, 0, Ui.dp(this, 16), 0);
        prev.setOnClickListener(v -> {
            perfM--;
            if (perfM < 1) {
                perfM = 12;
                perfY--;
            }
            render();
        });
        TextView nxt = Ui.text(this, "›", 24, Ui.DEEP, true);
        nxt.setPadding(Ui.dp(this, 16), 0, 0, 0);
        nxt.setOnClickListener(v -> {
            perfM++;
            if (perfM > 12) {
                perfM = 1;
                perfY++;
            }
            render();
        });
        nav2.addView(prev);
        nav2.addView(Ui.text(this, perfY + "년 " + perfM + "월", 18, Ui.INK, true));
        nav2.addView(nxt);
        content.addView(nav2);
        String key = String.format(Locale.KOREA, "%04d-%02d", perfY, perfM);
        final java.util.Map<Integer, Integer> sum = new java.util.HashMap<>();
        List<JSONObject> month = new ArrayList<>();
        int total = 0;
        for (JSONObject c : store.list(store.contracts())) {
            if (!c.optString("date").startsWith(key)) continue;
            month.add(c);
            int q = c.optInt("qty", 1);
            total += q;
            Integer old = sum.get(c.optInt("who"));
            sum.put(c.optInt("who"), (old == null ? 0 : old) + q);
        }
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.round(Ui.GOLD, 24, this));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        hero.addView(Ui.text(this, "이번 달 신규 계약", 14, Color.argb(200, 31, 27, 18), false));
        hero.addView(Ui.text(this, total + "대", 34, Ui.DARK, true));
        content.addView(hero, Ui.lp(this, 14));
        content.addView(Ui.text(this, "직원별 신규 계약", 18, Ui.INK, true), Ui.lp(this, 24));
        List<JSONObject> staff = activeStaff();
        Collections.sort(staff, (a, b) -> {
            Integer x = sum.get(b.optInt("id")), y = sum.get(a.optInt("id"));
            return (x == null ? 0 : x) - (y == null ? 0 : y);
        });
        int max = 1;
        for (Integer v : sum.values()) if (v > max) max = v;
        for (JSONObject u : staff) {
            Integer v = sum.get(u.optInt("id"));
            int n = v == null ? 0 : v;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView nm = Ui.text(this, u.optString("name"), 14, Ui.INK, false);
            row.addView(nm, new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(bar(n * 100 / max, Ui.GOLD, Ui.FIELD, 14), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView cnt = Ui.text(this, String.valueOf(n), 14, Ui.DEEP, true);
            cnt.setGravity(Gravity.END);
            row.addView(cnt, new LinearLayout.LayoutParams(Ui.dp(this, 36), ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(row, Ui.lp(this, 10));
        }
        content.addView(Ui.text(this, "올라온 계약", 18, Ui.INK, true), Ui.lp(this, 24));
        Collections.sort(month, (a, b) -> b.optString("created").compareTo(a.optString("created")));
        if (month.isEmpty()) content.addView(Ui.text(this, "이 달에 올라온 계약이 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 10));
        for (JSONObject c : month) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, c.optString("client") + "  ·  " + c.optString("product") + " " + c.optInt("qty", 1) + "대", 16, Ui.INK, true));
            String link = c.optString("linkType") + (c.optString("company").isEmpty() ? "" : " " + c.optString("company"));
            card.addView(Ui.text(this, link + "  ·  " + c.optString("kind") + "  ·  " + store.userName(c.optInt("who")) + "  ·  " + c.optString("date"), 12, Ui.GRAY, false), Ui.lp(this, 4));
            content.addView(card, Ui.lp(this, 8));
        }
    }

    private void showAddContract() {
        LinearLayout f = form();
        final EditText client = Ui.field(this, "가맹점 상호 (필수)", false);
        final Spinner product = spinner(new String[]{"토스 포스", "카드 단말기", "테이블오더", "키오스크", "기타"});
        final Spinner link = spinner(new String[]{"VAN사", "PG사"});
        final EditText company = Ui.field(this, "VAN사·PG사 업체명 (예: 다우데이타)", false);
        final Spinner kind = spinner(new String[]{"판매", "CMS", "렌탈"});
        final EditText qty = Ui.field(this, "수량", false);
        qty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        qty.setText("1");
        final EditText date = Ui.field(this, "계약일", false);
        date.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date()));
        f.addView(client);
        f.addView(Ui.text(this, "상품", 13, Ui.GRAY, false), Ui.lp(this, 8));
        f.addView(product);
        f.addView(Ui.text(this, "결제 연동", 13, Ui.GRAY, false), Ui.lp(this, 8));
        f.addView(link);
        f.addView(company);
        f.addView(Ui.text(this, "구분", 13, Ui.GRAY, false), Ui.lp(this, 8));
        f.addView(kind);
        f.addView(qty);
        f.addView(date);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        new AlertDialog.Builder(this).setTitle("신규 계약 올리기").setView(sv)
                .setNegativeButton("취소", null)
                .setPositiveButton("올리기", (d, w) -> {
                    String cl = client.getText().toString().trim();
                    String dt = date.getText().toString().trim();
                    int q;
                    try {
                        q = Integer.parseInt(qty.getText().toString().trim());
                    } catch (Exception e) {
                        q = 0;
                    }
                    if (cl.isEmpty() || q < 1 || !dt.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Ui.toast(this, "상호, 수량(1 이상), 날짜(2026-10-23 형식)를 확인해 주세요.");
                        return;
                    }
                    store.addContract(dt, cl, (String) product.getSelectedItem(), (String) link.getSelectedItem(),
                            company.getText().toString().trim(), (String) kind.getSelectedItem(), q, myId());
                    Ui.toast(this, "신규 계약을 올렸어요.");
                    render();
                }).show();
    }

    // ---------------- 전체 ----------------
    private void renderAll() {
        content.addView(title("전체"));
        LinearLayout prof = Ui.cardH(this);
        prof.addView(Ui.circle(this, me.optString("name"), 48, Ui.GOLD, Ui.DARK));
        LinearLayout pt = new LinearLayout(this);
        pt.setOrientation(LinearLayout.VERTICAL);
        pt.setPadding(Ui.dp(this, 14), 0, 0, 0);
        pt.addView(Ui.text(this, me.optString("name"), 17, Ui.INK, true));
        pt.addView(Ui.text(this, can(Perm.STAFF_MANAGE) ? "관리자" : "직원", 13, Ui.GRAY, false), Ui.lp(this, 2));
        prof.addView(pt);
        content.addView(prof, Ui.lp(this, 8));
        content.addView(sectionLabel("일"));
        content.addView(menuRow("재고", "준비 중", () -> Ui.toast(this, "재고는 서버 연결 후 만들어져요.")));
        content.addView(menuRow("일정", "", () -> {
            sub = "calendar";
            render();
        }));
        content.addView(menuRow("지난 업무 기록", "", () -> {
            sub = "history";
            render();
        }));
        content.addView(sectionLabel("회사"));
        int un = unreadNotices();
        content.addView(menuRow("공지사항", un > 0 ? String.valueOf(un) : "", () -> {
            sub = "notice";
            render();
        }));
        if (can(Perm.STAFF_MANAGE)) {
            content.addView(sectionLabel("관리자"));
            content.addView(menuRow("직원과 권한", "", () -> {
                sub = "staff";
                render();
            }));
        }
        content.addView(sectionLabel("계정"));
        content.addView(menuRow("로그아웃", "", () -> new AlertDialog.Builder(this).setTitle("로그아웃")
                .setMessage("로그아웃 할까요?").setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (d, w) -> {
                    me = null;
                    sub = null;
                    tab = "task";
                    showLogin();
                }).show()));
    }

    private TextView sectionLabel(String s) {
        TextView t = Ui.text(this, s, 13, Ui.GRAY, false);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 4));
        return t;
    }

    private View menuRow(String label, String badge, final Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        row.addView(Ui.text(this, label, 16, Ui.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!badge.isEmpty()) row.addView(Ui.text(this, badge, 12, Ui.DEEP, true));
        row.setOnClickListener(v -> r.run());
        return row;
    }

    // ---------------- 일정(달력) ----------------
    private static String dkey(int y, int m, int d) {
        return String.format(Locale.KOREA, "%04d-%02d-%02d", y, m, d);
    }

    private void renderCalendar() {
        Calendar today = Calendar.getInstance();
        String todayKey = dkey(today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH));
        if (calY == 0) {
            calY = today.get(Calendar.YEAR);
            calM = today.get(Calendar.MONTH) + 1;
            calSel = todayKey;
        }
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = Ui.text(this, "‹", 26, Ui.DEEP, true);
        prev.setPadding(0, 0, Ui.dp(this, 16), 0);
        prev.setOnClickListener(v -> {
            calM--;
            if (calM < 1) {
                calM = 12;
                calY--;
            }
            render();
        });
        TextView nxt = Ui.text(this, "›", 26, Ui.DEEP, true);
        nxt.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        nxt.setOnClickListener(v -> {
            calM++;
            if (calM > 12) {
                calM = 1;
                calY++;
            }
            render();
        });
        TextView td = Ui.text(this, "오늘", 14, Ui.DEEP, true);
        td.setOnClickListener(v -> {
            calY = today.get(Calendar.YEAR);
            calM = today.get(Calendar.MONTH) + 1;
            calSel = todayKey;
            render();
        });
        hd.addView(Ui.text(this, calY + "년 " + calM + "월", 24, Ui.INK, true));
        hd.addView(prev);
        hd.addView(nxt);
        hd.addView(td);
        content.addView(hd);

        LinearLayout wk = new LinearLayout(this);
        String[] wn = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            TextView t = Ui.text(this, wn[i], 12, i == 0 ? Color.rgb(214, 69, 69) : i == 6 ? Color.rgb(74, 111, 214) : Ui.GRAY, false);
            t.setGravity(Gravity.CENTER);
            wk.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        content.addView(wk, Ui.lp(this, 14));

        Calendar first = Calendar.getInstance();
        first.set(calY, calM - 1, 1);
        int offset = first.get(Calendar.DAY_OF_WEEK) - 1;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int day = 1 - offset;
        while (day <= days) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 7; c++, day++) {
                row.addView(dayCell(day, days, c, todayKey), new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f));
            }
            content.addView(row, Ui.lp(this, 2));
        }

        String sel = calSel;
        content.addView(Ui.text(this, sel + " 일정", 16, Ui.INK, true), Ui.lp(this, 18));
        String hn = Holidays.name(sel);
        boolean any = false;
        if (hn != null) {
            any = true;
            LinearLayout r = Ui.card(this);
            r.addView(Ui.text(this, hn + " (공휴일)", 15, Color.rgb(214, 69, 69), true));
            content.addView(r, Ui.lp(this, 8));
        }
        for (JSONObject t : store.list(store.tasks())) {
            if (!t.optString("due").startsWith(sel)) continue;
            if (!(can(Perm.TASK_VIEW_ALL) || t.optInt("assignee") == myId())) continue;
            any = true;
            LinearLayout r = Ui.card(this);
            r.addView(Ui.text(this, "[" + t.optString("type") + "] " + t.optString("title"), 15, Ui.INK, true));
            r.addView(Ui.text(this, store.userName(t.optInt("assignee")) + " · " + t.optString("status"), 12, Ui.GRAY, false), Ui.lp(this, 2));
            final int id = t.optInt("id");
            r.setOnClickListener(v -> showTask(id));
            content.addView(r, Ui.lp(this, 8));
        }
        for (JSONObject e : store.list(store.events())) {
            if (!sel.equals(e.optString("date"))) continue;
            any = true;
            LinearLayout r = Ui.card(this);
            r.setBackground(Ui.round(Ui.TINT, 20, this));
            r.addView(Ui.text(this, e.optString("title"), 15, Ui.INK, true));
            r.addView(Ui.text(this, store.userName(e.optInt("who")), 12, Ui.GRAY, false), Ui.lp(this, 2));
            content.addView(r, Ui.lp(this, 8));
        }
        if (!any) content.addView(Ui.text(this, "이 날은 일정이 없어요.", 14, Ui.GRAY, false), Ui.lp(this, 8));
    }

    private View dayCell(int day, int days, int col, String todayKey) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        if (day < 1 || day > days) return cell;
        final String key = dkey(calY, calM, day);
        String hn = Holidays.name(key);
        boolean red = col == 0 || hn != null;
        boolean isToday = key.equals(todayKey);
        boolean isSel = key.equals(calSel);
        int tc = red ? Color.rgb(214, 69, 69) : col == 6 ? Color.rgb(74, 111, 214) : Ui.INK;
        TextView n = Ui.text(this, String.valueOf(day), 15, isToday ? Ui.DARK : tc, isToday || isSel);
        n.setGravity(Gravity.CENTER);
        if (isToday) n.setBackground(Ui.round(Ui.GOLD, 17, this));
        else if (isSel) n.setBackground(Ui.round(Ui.TINT, 17, this));
        cell.addView(n, new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34)));
        if (hn != null) {
            TextView h = Ui.text(this, hn.length() > 5 ? hn.substring(0, 5) : hn, 9, Color.rgb(214, 69, 69), false);
            h.setGravity(Gravity.CENTER);
            h.setSingleLine(true);
            cell.addView(h);
        } else if (hasWork(key)) {
            View dot = new View(this);
            dot.setBackground(Ui.round(Ui.GOLD, 3, this));
            LinearLayout.LayoutParams dp5 = new LinearLayout.LayoutParams(Ui.dp(this, 6), Ui.dp(this, 6));
            dp5.topMargin = Ui.dp(this, 3);
            cell.addView(dot, dp5);
        }
        cell.setOnClickListener(v -> {
            calSel = key;
            render();
        });
        return cell;
    }

    private boolean hasWork(String key) {
        for (JSONObject t : store.list(store.tasks())) {
            if (t.optString("due").startsWith(key) && (can(Perm.TASK_VIEW_ALL) || t.optInt("assignee") == myId())) return true;
        }
        for (JSONObject e : store.list(store.events())) if (key.equals(e.optString("date"))) return true;
        return false;
    }

    private void showAddEvent() {
        LinearLayout f = form();
        final EditText ti = Ui.field(this, "일정 제목 (필수)", false);
        final EditText dt = Ui.field(this, "날짜", false);
        dt.setText(calSel.isEmpty() ? new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date()) : calSel);
        f.addView(ti);
        f.addView(dt);
        new AlertDialog.Builder(this).setTitle("일정 추가").setView(f)
                .setNegativeButton("취소", null)
                .setPositiveButton("추가", (d, w) -> {
                    String t = ti.getText().toString().trim();
                    String s = dt.getText().toString().trim();
                    if (t.isEmpty() || !s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        Ui.toast(this, "제목과 날짜(2026-10-23 형식)를 확인해 주세요.");
                        return;
                    }
                    store.addEvent(s, t, myId());
                    calSel = s;
                    render();
                }).show();
    }
}
