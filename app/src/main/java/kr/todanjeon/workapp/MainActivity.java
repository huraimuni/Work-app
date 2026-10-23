package kr.todanjeon.workapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.content.res.Configuration;
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
    private Runnable lastAuth = null;
    private int calY = 0, calM = 0;
    private String calSel = "";
    private int perfY = 0, perfM = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Cloud.init(this);
        store = new Store(this);
        showSplash();
        new Handler().postDelayed(() -> {
            if (Cloud.auth().getCurrentUser() != null && store.sessionId() != 0) enterApp();
            else showLogin();
        }, 1800);
    }

    /** 로그인된 상태로 앱에 들어간다: 서버와 실시간 연결하고, 내 정보가 도착하면 화면을 그린다. */
    private void enterApp() {
        store.attach(store.sessionId(), store.cachedPerf(), store.cachedReport(), this::onRemote);
        me = store.sessionUser();
        if (me != null) buildMain();
        else showLoading();
    }

    private void onRemote() {
        if (store.sessionId() == 0) return;
        JSONObject u = store.user(store.sessionId());
        if (u != null && !u.optBoolean("active", true)) {
            doLogout();
            Ui.toast(this, "사용이 중지된 계정입니다.");
            return;
        }
        if (me == null) {
            me = store.sessionUser();
            if (me != null) buildMain();
        } else if (nav != null) {
            if (chatRoom != 0 && "chat".equals(tab) && sub == null && msgBox != null) fillMsgs();
            else render();
        }
    }

    private void showLoading() {
        lastAuth = this::showLoading;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.addView(Ui.text(this, "서버에서 내 정보를 불러오는 중입니다…", 15, Ui.GRAY, false));
        Button out = Ui.button(this, "취소하고 처음으로", Ui.FIELD, Ui.INK);
        out.setOnClickListener(v -> doLogout());
        card.addView(out, Ui.lp(this, 16));
        setContentView(authScreen("잠시만요.", "인터넷 연결을 확인해 주세요.", card));
    }

    private void doLogout() {
        store.detach();
        Cloud.auth().signOut();
        store.clear();
        me = null;
        sub = null;
        tab = "task";
        showLogin();
    }

    /** 폴드를 펼치거나 돌려도 앱이 새로 시작되지 않고, 화면 폭에 맞게 다시 그린다. */
    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (me != null) buildMain();
        else if (lastAuth != null) lastAuth.run();
    }

    private int widthDp() {
        return getResources().getConfiguration().screenWidthDp;
    }

    /** 폴드 펼침·태블릿 등 넓은 화면(너비 600dp 이상). */
    private boolean wide() {
        return widthDp() >= 600;
    }

    private static final int RAIL_DP = 96;
    private static final int MAX_CONTENT_DP = 680;

    /** 넓은 화면에서 내용이 가운데 오도록 양옆에 두는 여백(dp). */
    private int centerOffsetDp() {
        return Math.max(0, (widthDp() - RAIL_DP - MAX_CONTENT_DP) / 2);
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
        int side = Math.max(Ui.dp(this, 24), (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 440)) / 2);
        l.setPadding(side, Ui.dp(this, 24), side, Ui.dp(this, 24));
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

    // ---------------- 휴대폰 문자 인증 로그인 ----------------
    private String pendingPhone = "";
    private String verificationId = null;
    private com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken resendToken = null;

    private void showLogin() {
        lastAuth = this::showLogin;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        final EditText phone = Ui.field(this, "휴대폰 번호 (예: 01012345678)", false);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        if (!pendingPhone.isEmpty()) phone.setText(pendingPhone);
        card.addView(phone);
        final Button go = Ui.button(this, "인증번호 받기", Ui.GOLD, Ui.DARK);
        go.setOnClickListener(v -> {
            String raw = phone.getText().toString().trim();
            if (raw.replaceAll("[^0-9]", "").length() < 10) {
                Ui.toast(this, "휴대폰 번호를 정확히 입력해 주세요.");
                return;
            }
            pendingPhone = raw;
            go.setEnabled(false);
            go.setText("문자 보내는 중…");
            sendCode(Cloud.e164(raw), go);
        });
        card.addView(go, Ui.lp(this, 16));
        setContentView(authScreen("오늘도 반갑습니다.", "등록된 휴대폰 번호로 문자 인증을 해 주세요.\n(한 번 로그인하면 계속 유지됩니다)", card));
    }

    private void sendCode(final String e164, final Button go) {
        com.google.firebase.auth.PhoneAuthOptions.Builder ob = com.google.firebase.auth.PhoneAuthOptions.newBuilder(Cloud.auth())
                .setPhoneNumber(e164)
                .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(com.google.firebase.auth.PhoneAuthCredential cred) {
                        signIn(cred, e164);
                    }

                    @Override
                    public void onVerificationFailed(com.google.firebase.FirebaseException e) {
                        showLogin();
                        new AlertDialog.Builder(MainActivity.this).setTitle("문자 인증을 시작하지 못했습니다")
                                .setMessage(String.valueOf(e.getMessage())).setPositiveButton("확인", null).show();
                    }

                    @Override
                    public void onCodeSent(String id, com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken t) {
                        verificationId = id;
                        resendToken = t;
                        showCode(e164);
                    }
                });
        com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(ob.build());
    }

    private void showCode(final String e164) {
        lastAuth = () -> showCode(e164);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        final EditText code = Ui.field(this, "문자로 받은 인증번호 6자리", false);
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        card.addView(code);
        Button go = Ui.button(this, "확인", Ui.GOLD, Ui.DARK);
        go.setOnClickListener(v -> {
            String c = code.getText().toString().trim();
            if (c.length() < 6 || verificationId == null) {
                Ui.toast(this, "인증번호 6자리를 입력해 주세요.");
                return;
            }
            signIn(com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, c), e164);
        });
        card.addView(go, Ui.lp(this, 16));
        Button back = Ui.button(this, "번호 다시 입력", Ui.FIELD, Ui.INK);
        back.setOnClickListener(v -> showLogin());
        card.addView(back, Ui.lp(this, 8));
        setContentView(authScreen("문자를 보냈어요.", e164 + " 로 보낸 인증번호를 입력해 주세요.", card));
    }

    private void signIn(com.google.firebase.auth.PhoneAuthCredential cred, final String e164) {
        Cloud.auth().signInWithCredential(cred).addOnSuccessListener(r ->
                Cloud.fs().collection("phones").document(e164).get().addOnSuccessListener(doc -> {
                    if (!doc.exists() || !Boolean.TRUE.equals(doc.getBoolean("active"))) {
                        Cloud.auth().signOut();
                        Ui.toast(this, "등록되지 않은 번호입니다. 관리자에게 등록을 요청해 주세요.");
                        showLogin();
                        return;
                    }
                    final long uid = doc.getLong("uid") == null ? 0 : doc.getLong("uid");
                    final String name = doc.getString("name") == null ? "" : doc.getString("name");
                    final boolean admin = Boolean.TRUE.equals(doc.getBoolean("admin"));
                    final boolean perf = Boolean.TRUE.equals(doc.getBoolean("perf"));
                    final boolean report = Boolean.TRUE.equals(doc.getBoolean("report"));
                    store.clear();
                    store.setSession((int) uid);
                    store.setCaps(perf || admin);
                    store.setReportCap(report || admin);
                    Cloud.fs().collection("users").document(String.valueOf(uid)).get().addOnSuccessListener(ud -> {
                        if (!ud.exists()) store.ensureUser((int) uid, name, e164, admin, perf);
                        enterApp();
                    }).addOnFailureListener(e -> enterApp());
                }).addOnFailureListener(e -> {
                    Cloud.auth().signOut();
                    Ui.toast(this, "등록 정보를 확인하지 못했습니다: " + e.getMessage());
                    showLogin();
                })
        ).addOnFailureListener(e -> {
            Ui.toast(this, "인증번호가 맞지 않습니다.");
            showCode(e164);
        });
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
        final boolean wide = wide();
        final int off = wide ? centerOffsetDp() : 0;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(0, Ui.dp(this, 48), 0, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 20 + off), Ui.dp(this, 8), Ui.dp(this, 20 + off), Ui.dp(this, 12));
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
        scroll = sv;
        sv.setVerticalScrollBarEnabled(false);
        FrameLayout holder = new FrameLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, wide ? 48 : 160));
        int cw = wide ? Ui.dp(this, Math.min(MAX_CONTENT_DP, widthDp() - RAIL_DP)) : ViewGroup.LayoutParams.MATCH_PARENT;
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(cw, ViewGroup.LayoutParams.WRAP_CONTENT);
        cl.gravity = Gravity.CENTER_HORIZONTAL;
        holder.addView(content, cl);
        sv.addView(holder);
        main.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new LinearLayout(this);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(Ui.dp(this, wide ? 4 : 10));
        if (wide) {
            nav.setOrientation(LinearLayout.VERTICAL);
            nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 56), Ui.dp(this, 8), Ui.dp(this, 16));
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.logo9);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setContentDescription("나인페이먼츠 로고");
            LinearLayout.LayoutParams lg = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
            lg.gravity = Gravity.CENTER_HORIZONTAL;
            lg.bottomMargin = Ui.dp(this, 20);
            nav.addView(logo, lg);
            LinearLayout outer = new LinearLayout(this);
            outer.setOrientation(LinearLayout.HORIZONTAL);
            outer.addView(nav, new LinearLayout.LayoutParams(Ui.dp(this, RAIL_DP), ViewGroup.LayoutParams.MATCH_PARENT));
            outer.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            root.addView(outer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setGravity(Gravity.CENTER_VERTICAL);
            nav.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6));
            root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        fab = Ui.text(this, "+", 28, Ui.DARK, false);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Ui.round(Ui.GOLD, 28, this));
        fab.setElevation(Ui.dp(this, 6));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56));
        fp.gravity = Gravity.BOTTOM | Gravity.END;
        fp.rightMargin = Ui.dp(this, wide ? 24 + off : 20);
        fp.bottomMargin = Ui.dp(this, wide ? 32 : 84);
        root.addView(fab, fp);

        if (!wide) {
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 64));
            np.gravity = Gravity.BOTTOM;
            root.addView(nav, np);
        }
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
        if (me != null) {
            JSONObject fresh = store.user(me.optInt("id"));
            if (fresh != null) me = fresh;
        }
        // 넓은 화면의 왼쪽 메뉴는 맨 위 로고(첫 번째 자식)를 남기고 메뉴만 다시 만든다.
        int keep = wide() ? 1 : 0;
        while (nav.getChildCount() > keep) nav.removeViewAt(nav.getChildCount() - 1);
        addNav("task", "업무", newCount());
        addNav("project", "프로젝트", 0);
        addNav("chat", "채팅", 0);
        addNav("calendar", "일정", 0);
        addNav("all", "전체", unreadNotices());
        content.removeAllViews();
        String k = sub != null ? sub : tab;
        if (sub != null) addBack();
        if ("task".equals(k)) renderTasks();
        else if ("project".equals(k)) renderProjects();
        else if ("chat".equals(k)) renderChat();
        else if ("perf".equals(k)) renderPerf(false);
        else if ("myperf".equals(k)) renderPerf(true);
        else if ("daily".equals(k)) renderDaily();
        else if ("reportview".equals(k)) renderReportView();
        else if ("perfreport".equals(k)) renderPerfReport();
        else if ("calendar".equals(k)) renderCalendar();
        else if ("notice".equals(k)) renderNotices();
        else if ("history".equals(k)) renderHistory();
        else if ("staff".equals(k)) renderStaff();
        else if ("merchants".equals(k)) renderMerchants();
        else if ("stock".equals(k)) renderStock();
        else if ("contracts".equals(k)) renderContracts();
        else if ("manual".equals(k)) renderManual();
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
        } else if ("perf".equals(k) || "myperf".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("신규 계약 올리기");
            fab.setOnClickListener(v -> showAddContract());
        } else if ("calendar".equals(k) && can(Perm.STAFF_MANAGE)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("일정 추가");
            fab.setOnClickListener(v -> showAddEvent());
        } else if ("task".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("업무 지시");
            fab.setOnClickListener(v -> showCreateTask());
        } else if ("notice".equals(k) && can(Perm.NOTICE_WRITE)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("공지 작성");
            fab.setOnClickListener(v -> showCreateNotice());
        } else if ("stock".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("재고 추가");
            fab.setOnClickListener(v -> showAddStock());
        } else if ("merchants".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("가맹점 등록");
            fab.setOnClickListener(v -> showEditMerchant(null, null));
        } else if ("contracts".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("신규 계약 올리기");
            fab.setOnClickListener(v -> showAddContract());
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
        if (wide()) {
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
            wp.bottomMargin = Ui.dp(this, 6);
            nav.addView(t, wp);
        } else {
            nav.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
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
        List<JSONObject> expList = expiringContracts();
        if (!expList.isEmpty()) {
            LinearLayout al = Ui.card(this);
            al.setBackground(Ui.round(Color.rgb(253, 236, 234), 20, this));
            JSONObject first = expList.get(0);
            al.addView(Ui.text(this, "계약 만료가 가까운 가맹점 " + expList.size() + "곳", 15, Ui.RED, true));
            al.addView(Ui.text(this, first.optString("client") + " · " + dday(daysLeft(first.optString("expire"))) + (expList.size() > 1 ? " 외 " + (expList.size() - 1) + "곳" : "") + "  (눌러서 보기)", 12, Ui.GRAY, false), Ui.lp(this, 2));
            al.setOnClickListener(v -> {
                tab = "all";
                sub = "contracts";
                render();
            });
            content.addView(al, Ui.lp(this, 12));
        }

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
        Button mp = Ui.button(this, "가맹점 조회 (정보 자동 입력)", Ui.TINT, Ui.DEEP);
        mp.setOnClickListener(v -> pickMerchant(m -> {
            client.setText(m.optString("name"));
            phone.setText(m.optString("phone"));
            addr.setText(m.optString("addr"));
        }));
        f.addView(mp, Ui.lp(this, 8));
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
        if (t.optInt("creator") == myId() || can(Perm.STAFF_MANAGE)) {
            b.setNeutralButton("삭제", (d, w) -> new AlertDialog.Builder(this).setTitle("업무를 삭제할까요?")
                    .setMessage(t.optString("title") + "\n목록과 달력에서 사라집니다.")
                    .setNegativeButton("취소", null).setPositiveButton("삭제", (d2, w2) -> {
                        try {
                            t.put("deleted", true);
                        } catch (Exception ignored) {
                        }
                        store.save();
                        render();
                    }).show());
        }
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
        final EditText pin = Ui.field(this, "휴대폰 번호 (예: 01012345678)", false);
        pin.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
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
                    String p = Cloud.e164(pin.getText().toString());
                    if (n.isEmpty() || p.length() < 12) {
                        Ui.toast(this, "이름과 휴대폰 번호를 입력해 주세요.");
                    } else if (store.nameExists(n)) {
                        Ui.toast(this, "같은 이름의 직원이 있습니다.");
                    } else if (store.phoneExists(p)) {
                        Ui.toast(this, "이미 등록된 번호입니다.");
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
    

    // ---------------- 실적 ----------------
    

    

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
        content.addView(menuRow("재고", "", () -> {
            sub = "stock";
            render();
        }));
        content.addView(menuRow("가맹점", "", () -> {
            sub = "merchants";
            render();
        }));
        int ex = expiringContracts().size();
        content.addView(menuRow("계약 관리 (설치 완료·만료 알림)", ex > 0 ? "만료 " + ex : "", () -> {
            sub = "contracts";
            render();
        }));
        content.addView(menuRow("신규 계약 올리기", "", () -> showAddContract()));
        content.addView(menuRow("내 실적", "", () -> {
            sub = "myperf";
            render();
        }));
        if (canPerf()) {
            content.addView(menuRow("전체 실적·직원별 순위", "", () -> {
                sub = "perf";
                render();
            }));
            content.addView(menuRow("실적 보고서 올리기 (대표님께)", "", () -> {
                sub = "perfreport";
                render();
            }));
        }
        content.addView(menuRow("일일보고 쓰기", "", () -> {
            sub = "daily";
            render();
        }));
        if (canReport()) {
            content.addView(menuRow("일일보고 종합 보기", "", () -> {
                sub = "reportview";
                render();
            }));
            if (!canPerf()) {
                content.addView(menuRow("실적 보고서 보기", "", () -> {
                    sub = "perfreport";
                    render();
                }));
            }
        }
        content.addView(menuRow("지난 업무 기록", "", () -> {
            sub = "history";
            render();
        }));
        content.addView(sectionLabel("회사"));
        content.addView(menuRow("사용설명서", "", () -> {
            sub = "manual";
            render();
        }));
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
                .setPositiveButton("로그아웃", (d, w) -> doLogout()).show()));
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
        if (!can(Perm.STAFF_MANAGE)) {
            content.addView(Ui.text(this, "내 일정은 업무 탭의 + 버튼으로 업무를 올리면 달력에 자동으로 들어가요.", 12, Ui.GRAY, false), Ui.lp(this, 6));
        }

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
            content.addView(eventCard(e, false), Ui.lp(this, 8));
        }
        if (!any) content.addView(Ui.text(this, "이 날은 일정이 없어요.", 14, Ui.GRAY, false), Ui.lp(this, 8));
        addUpcoming();
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

    

    // ======================= v0.5: 가맹점 · 재고 · 계약 · 채팅 · 설명서 =======================
    private int chatRoom = 0;
    private String draft = "";
    private LinearLayout msgBox;
    private ScrollView scroll;

    private static String won(long v) {
        return java.text.NumberFormat.getInstance(Locale.KOREA).format(v) + "원";
    }

    private static String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
    }

    private static int daysLeft(String ymd) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            long a = f.parse(todayStr()).getTime();
            long b = f.parse(ymd).getTime();
            return (int) Math.round((b - a) / 86400000.0);
        } catch (Exception e) {
            return 99999;
        }
    }

    private static String addMonths(String ymd, int months) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(ymd));
            c.add(Calendar.MONTH, months);
            return f.format(c.getTime());
        } catch (Exception e) {
            return "";
        }
    }

    private static String gradeLabel(int g) {
        if (g < 0 || g >= Store.GRADES.length || Store.GRADES[g].isEmpty()) return "";
        return g + "코드 · " + Store.GRADES[g];
    }

    private static String dday(int d) {
        if (d == 99999) return "";
        if (d < 0) return "만료 " + (-d) + "일 지남";
        if (d == 0) return "오늘 만료";
        return "D-" + d;
    }

    private String merchantInfo(JSONObject m) {
        StringBuilder b = new StringBuilder();
        b.append(m.optString("name"));
        if (!m.optString("bizno").isEmpty()) b.append("\n사업자  ").append(m.optString("bizno"));
        if (!m.optString("phone").isEmpty()) b.append("\n연락처  ").append(m.optString("phone"));
        if (!m.optString("addr").isEmpty()) b.append("\n주소  ").append(m.optString("addr"));
        if (!m.optString("terms").isEmpty()) b.append("\n계약조건  ").append(m.optString("terms"));
        b.append("\n등급  ").append(gradeLabel(m.optInt("grade", 5)));
        if (!m.optString("memo").isEmpty()) b.append("\n메모  ").append(m.optString("memo"));
        return b.toString();
    }

    private int gradeColor(int g) {
        if (g <= 0) return Ui.GREEN;
        if (g == 1) return Ui.DEEP;
        if (g <= 3) return Ui.GRAY;
        if (g == 4) return Ui.RED;
        return Color.rgb(106, 63, 196);
    }

    // ---------- 가맹점 ----------
    private List<JSONObject> sortedMerchants() {
        List<JSONObject> l = store.list(store.arr("merchants"));
        Collections.sort(l, (a, b) -> a.optString("name").compareTo(b.optString("name")));
        return l;
    }

    private void pickMerchant(final java.util.function.Consumer<JSONObject> cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        final EditText q = Ui.field(this, "상호·사업자번호·전화·주소 검색 (초성도 돼요)", false);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(q);
        box.addView(list);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("가맹점 조회").setView(sv)
                .setNegativeButton("닫기", null)
                .setNeutralButton("새 가맹점 등록", (d, w) -> showEditMerchant(null, cb)).create();
        final Runnable fill = () -> {
            list.removeAllViews();
            String s = q.getText().toString();
            int n = 0;
            for (JSONObject m : sortedMerchants()) {
                if (!Kor.match(m.optString("name") + " " + m.optString("bizno") + " " + m.optString("phone") + " " + m.optString("addr"), s)) continue;
                if (++n > 40) break;
                final JSONObject mm = m;
                LinearLayout row = Ui.cardH(this);
                LinearLayout mid = new LinearLayout(this);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.addView(Ui.text(this, m.optString("name"), 16, Ui.INK, true));
                mid.addView(Ui.text(this, (m.optString("phone").isEmpty() ? "" : m.optString("phone") + " · ") + m.optString("addr"), 12, Ui.GRAY, false), Ui.lp(this, 2));
                row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(Ui.chip(this, gradeLabel(m.optInt("grade", 5)), Ui.FIELD, gradeColor(m.optInt("grade", 5))));
                row.setOnClickListener(v -> {
                    dlg.dismiss();
                    cb.accept(mm);
                });
                list.addView(row, Ui.lp(this, 8));
            }
            if (n == 0) list.addView(Ui.text(this, "찾는 가맹점이 없습니다. 아래 '새 가맹점 등록'을 눌러 주세요.", 14, Ui.GRAY, false), Ui.lp(this, 12));
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                fill.run();
            }
        });
        fill.run();
        dlg.show();
    }

    private void showEditMerchant(final JSONObject existing, final java.util.function.Consumer<JSONObject> cb) {
        LinearLayout f = form();
        final EditText name = Ui.field(this, "상호 (필수)", false);
        final EditText bizno = Ui.field(this, "사업자번호 (예: 123-45-67890)", false);
        final EditText phone = Ui.field(this, "연락처", false);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        final EditText addr = Ui.field(this, "주소", false);
        final EditText terms = Ui.field(this, "계약조건 (예: 렌탈 36개월, 월 3만원)", false);
        terms.setSingleLine(false);
        final EditText memo = Ui.field(this, "메모", false);
        memo.setSingleLine(false);
        String[] gl = new String[Store.GRADES.length];
        for (int i = 0; i < gl.length; i++) gl[i] = gradeLabel(i).isEmpty() ? "(사용 안 함)" : gradeLabel(i);
        final Spinner grade = spinner(gl);
        grade.setSelection(5);
        if (existing != null) {
            name.setText(existing.optString("name"));
            bizno.setText(existing.optString("bizno"));
            phone.setText(existing.optString("phone"));
            addr.setText(existing.optString("addr"));
            terms.setText(existing.optString("terms"));
            memo.setText(existing.optString("memo"));
            grade.setSelection(Math.max(0, Math.min(7, existing.optInt("grade", 5))));
        }
        f.addView(name);
        f.addView(bizno);
        f.addView(phone);
        f.addView(addr);
        f.addView(terms);
        f.addView(Ui.text(this, "등급 코드", 13, Ui.GRAY, false), Ui.lp(this, 8));
        f.addView(grade);
        f.addView(memo);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle(existing == null ? "가맹점 등록" : "가맹점 정보 수정").setView(sv)
                .setNegativeButton("취소", null).setPositiveButton("저장", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                Ui.toast(this, "상호를 입력해 주세요.");
                return;
            }
            JSONObject m = existing != null ? existing : store.create("merchants");
            try {
                m.put("name", n);
                m.put("bizno", bizno.getText().toString().trim());
                m.put("phone", phone.getText().toString().trim());
                m.put("addr", addr.getText().toString().trim());
                m.put("terms", terms.getText().toString().trim());
                m.put("grade", grade.getSelectedItemPosition());
                m.put("memo", memo.getText().toString().trim());
            } catch (Exception ignored) {
            }
            store.save();
            dlg.dismiss();
            Ui.toast(this, "저장했어요.");
            if (cb != null) cb.accept(m);
            else render();
        });
    }

    private void showMerchant(final JSONObject m) {
        LinearLayout l = form();
        TextView t = Ui.text(this, merchantInfo(m), 15, Ui.INK, false);
        t.setLineSpacing(0, 1.3f);
        l.addView(t);
        new AlertDialog.Builder(this).setTitle("가맹점 정보").setView(l)
                .setNegativeButton("닫기", null)
                .setPositiveButton("수정", (d, w) -> showEditMerchant(m, x -> render())).show();
    }

    private void renderMerchants() {
        content.addView(title("가맹점"));
        content.addView(Ui.text(this, "우리가 관리하는 가맹점 목록이에요. 오른쪽 아래 + 로 새로 등록하고, 눌러서 수정합니다.", 13, Ui.GRAY, false));
        final EditText q = Ui.field(this, "상호·번호·주소 검색 (초성도 돼요)", false);
        content.addView(q, Ui.lp(this, 10));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        final Runnable fill = () -> {
            list.removeAllViews();
            int n = 0;
            for (JSONObject m : sortedMerchants()) {
                if (!Kor.match(m.optString("name") + " " + m.optString("bizno") + " " + m.optString("phone") + " " + m.optString("addr"), q.getText().toString())) continue;
                n++;
                final JSONObject mm = m;
                LinearLayout row = Ui.cardH(this);
                LinearLayout mid = new LinearLayout(this);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.addView(Ui.text(this, m.optString("name"), 16, Ui.INK, true));
                mid.addView(Ui.text(this, (m.optString("phone").isEmpty() ? "" : m.optString("phone") + " · ") + m.optString("addr"), 12, Ui.GRAY, false), Ui.lp(this, 2));
                row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(Ui.chip(this, gradeLabel(m.optInt("grade", 5)), Ui.FIELD, gradeColor(m.optInt("grade", 5))));
                row.setOnClickListener(v -> showMerchant(mm));
                list.addView(row, Ui.lp(this, 8));
            }
            if (n == 0) list.addView(Ui.text(this, "등록된 가맹점이 없습니다. 오른쪽 아래 + 로 등록해 주세요.", 14, Ui.GRAY, false), Ui.lp(this, 16));
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                fill.run();
            }
        });
        fill.run();
    }

    // ---------- 재고 ----------
    private void renderStock() {
        content.addView(title("재고"));
        content.addView(Ui.text(this, "장비를 직접 입력해 관리해요. 계약에서 장비를 고르면 '출고중'이 되고, 설치 완료하면 재고에서 빠집니다.", 13, Ui.GRAY, false));
        final EditText q = Ui.field(this, "모델명·시리얼 검색 (초성도 돼요)", false);
        content.addView(q, Ui.lp(this, 10));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        final Runnable fill = () -> {
            list.removeAllViews();
            java.util.Map<String, List<JSONObject>> g = new java.util.TreeMap<>();
            for (JSONObject r : store.list(store.arr("stock"))) {
                if (r.optBoolean("deleted") || "설치완료".equals(r.optString("status"))) continue;
                if (!Kor.match(r.optString("model") + " " + r.optString("serial"), q.getText().toString())) continue;
                if (!g.containsKey(r.optString("model"))) g.put(r.optString("model"), new ArrayList<JSONObject>());
                g.get(r.optString("model")).add(r);
            }
            if (g.isEmpty()) list.addView(Ui.text(this, "등록된 재고가 없습니다. 오른쪽 아래 + 로 추가해 주세요.", 14, Ui.GRAY, false), Ui.lp(this, 16));
            for (java.util.Map.Entry<String, List<JSONObject>> e : g.entrySet()) {
                int have = 0, out = 0, safe = 0;
                for (JSONObject r : e.getValue()) {
                    if ("출고중".equals(r.optString("status"))) out += r.optInt("qty", 1);
                    else have += r.optInt("qty", 1);
                    safe = Math.max(safe, r.optInt("safe"));
                }
                LinearLayout card = Ui.card(this);
                LinearLayout head = new LinearLayout(this);
                head.setGravity(Gravity.CENTER_VERTICAL);
                head.addView(Ui.text(this, e.getKey(), 16, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                boolean low = safe > 0 && have < safe;
                head.addView(Ui.chip(this, "재고 " + have + (out > 0 ? " · 출고중 " + out : ""), low ? Color.rgb(253, 236, 234) : Ui.TINT, low ? Ui.RED : Ui.DEEP));
                card.addView(head);
                if (safe > 0) card.addView(Ui.text(this, "안전재고 " + safe + "대" + (low ? " — 부족해요!" : ""), 12, low ? Ui.RED : Ui.GRAY, low), Ui.lp(this, 4));
                for (final JSONObject r : e.getValue()) {
                    String s = r.optString("serial").isEmpty() ? "시리얼 없음 · " + r.optInt("qty", 1) + "개" : r.optString("serial");
                    TextView t = Ui.text(this, s + ("출고중".equals(r.optString("status")) ? "   (출고중)" : ""), 14, "출고중".equals(r.optString("status")) ? Ui.GRAY : Ui.INK, false);
                    t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
                    t.setOnClickListener(v -> showStockRow(r));
                    card.addView(t);
                }
                list.addView(card, Ui.lp(this, 10));
            }
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                fill.run();
            }
        });
        fill.run();
    }

    private void showAddStock() {
        LinearLayout f = form();
        final EditText model = Ui.field(this, "모델명 (예: 토스 포스 T2)", false);
        final EditText serials = Ui.field(this, "시리얼 (여러 개는 줄을 바꿔 한 줄에 하나)", false);
        serials.setSingleLine(false);
        serials.setMinLines(3);
        final EditText qty = Ui.field(this, "시리얼이 없는 물품의 수량 (선택)", false);
        qty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText safe = Ui.field(this, "안전재고 (이 수 아래면 알려줘요, 선택)", false);
        safe.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        f.addView(model);
        f.addView(serials);
        f.addView(qty);
        f.addView(safe);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("재고 추가").setView(sv)
                .setNegativeButton("취소", null).setPositiveButton("추가", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String m = model.getText().toString().trim();
            if (m.isEmpty()) {
                Ui.toast(this, "모델명을 입력해 주세요.");
                return;
            }
            int sf = 0, qn = 0;
            try {
                sf = Integer.parseInt(safe.getText().toString().trim());
            } catch (Exception ignored) {
            }
            try {
                qn = Integer.parseInt(qty.getText().toString().trim());
            } catch (Exception ignored) {
            }
            List<String> ss = new ArrayList<>();
            for (String line : serials.getText().toString().split("[\\n,]+")) if (!line.trim().isEmpty()) ss.add(line.trim());
            if (ss.isEmpty() && qn < 1) {
                Ui.toast(this, "시리얼을 적거나 수량을 입력해 주세요.");
                return;
            }
            try {
                if (ss.isEmpty()) {
                    JSONObject r = store.create("stock");
                    r.put("model", m);
                    r.put("serial", "");
                    r.put("qty", qn);
                    r.put("safe", sf);
                    r.put("status", "재고");
                    r.put("deleted", false);
                } else {
                    for (String s : ss) {
                        JSONObject r = store.create("stock");
                        r.put("model", m);
                        r.put("serial", s);
                        r.put("qty", 1);
                        r.put("safe", sf);
                        r.put("status", "재고");
                        r.put("deleted", false);
                    }
                }
            } catch (Exception ignored) {
            }
            store.save();
            dlg.dismiss();
            Ui.toast(this, "재고에 추가했어요.");
            render();
        });
    }

    private void showStockRow(final JSONObject r) {
        LinearLayout f = form();
        final EditText model = Ui.field(this, "모델명", false);
        model.setText(r.optString("model"));
        final EditText serial = Ui.field(this, "시리얼", false);
        serial.setText(r.optString("serial"));
        final EditText safe = Ui.field(this, "안전재고", false);
        safe.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        safe.setText(String.valueOf(r.optInt("safe")));
        f.addView(model);
        f.addView(serial);
        f.addView(safe);
        if ("출고중".equals(r.optString("status"))) {
            JSONObject mer = store.byId("merchants", r.optInt("merchant"));
            f.addView(Ui.text(this, "출고중 · " + (mer == null ? "" : mer.optString("name")), 13, Ui.GRAY, false), Ui.lp(this, 8));
        }
        new AlertDialog.Builder(this).setTitle("재고 수정").setView(f)
                .setNegativeButton("닫기", null)
                .setNeutralButton("삭제", (d, w) -> new AlertDialog.Builder(this).setTitle("삭제할까요?")
                        .setMessage(r.optString("model") + " " + r.optString("serial") + "\n목록에서 지워집니다.")
                        .setNegativeButton("취소", null).setPositiveButton("삭제", (d2, w2) -> {
                            try {
                                r.put("deleted", true);
                            } catch (Exception ignored) {
                            }
                            store.save();
                            render();
                        }).show())
                .setPositiveButton("저장", (d, w) -> {
                    try {
                        r.put("model", model.getText().toString().trim());
                        r.put("serial", serial.getText().toString().trim());
                        int sf = 0;
                        try {
                            sf = Integer.parseInt(safe.getText().toString().trim());
                        } catch (Exception ignored) {
                        }
                        r.put("safe", sf);
                    } catch (Exception ignored) {
                    }
                    store.save();
                    render();
                }).show();
    }

    /** 재고에서 장비를 고르는 창. 직접 입력도 가능. */
    private void pickStock(final java.util.function.Consumer<List<JSONObject>> cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        final EditText q = Ui.field(this, "모델명·시리얼 검색", false);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        box.addView(q);
        box.addView(list);
        final java.util.Set<Integer> chosen = new java.util.HashSet<>();
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        final Runnable fill = () -> {
            list.removeAllViews();
            int n = 0;
            for (final JSONObject r : store.list(store.arr("stock"))) {
                if (r.optBoolean("deleted") || !"재고".equals(r.optString("status"))) continue;
                if (!Kor.match(r.optString("model") + " " + r.optString("serial"), q.getText().toString())) continue;
                if (++n > 80) break;
                CheckBox cb2 = new CheckBox(this);
                cb2.setText(r.optString("model") + (r.optString("serial").isEmpty() ? "  (" + r.optInt("qty", 1) + "개 보유)" : "  ·  " + r.optString("serial")));
                cb2.setChecked(chosen.contains(r.optInt("id")));
                cb2.setOnCheckedChangeListener((b, on) -> {
                    if (on) chosen.add(r.optInt("id"));
                    else chosen.remove(r.optInt("id"));
                });
                list.addView(cb2);
            }
            if (n == 0) list.addView(Ui.text(this, "재고에 장비가 없습니다. '직접 입력'을 눌러 주세요.", 14, Ui.GRAY, false), Ui.lp(this, 12));
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                fill.run();
            }
        });
        fill.run();
        new AlertDialog.Builder(this).setTitle("장비 선택 (재고)").setView(sv)
                .setNegativeButton("닫기", null)
                .setNeutralButton("직접 입력", (d, w) -> {
                    LinearLayout f = form();
                    final EditText model = Ui.field(this, "모델명", false);
                    final EditText serial = Ui.field(this, "시리얼", false);
                    f.addView(model);
                    f.addView(serial);
                    new AlertDialog.Builder(this).setTitle("장비 직접 입력").setView(f)
                            .setNegativeButton("취소", null)
                            .setPositiveButton("추가", (d2, w2) -> {
                                if (model.getText().toString().trim().isEmpty()) {
                                    Ui.toast(this, "모델명을 입력해 주세요.");
                                    return;
                                }
                                JSONObject e = new JSONObject();
                                try {
                                    e.put("sid", 0);
                                    e.put("model", model.getText().toString().trim());
                                    e.put("serial", serial.getText().toString().trim());
                                } catch (Exception ignored) {
                                }
                                List<JSONObject> one = new ArrayList<>();
                                one.add(e);
                                cb.accept(one);
                            }).show();
                })
                .setPositiveButton("선택 완료", (d, w) -> {
                    List<JSONObject> out = new ArrayList<>();
                    for (Integer id : chosen) {
                        JSONObject r = store.byId("stock", id);
                        if (r == null) continue;
                        JSONObject e = new JSONObject();
                        try {
                            e.put("sid", id);
                            e.put("model", r.optString("model"));
                            e.put("serial", r.optString("serial"));
                        } catch (Exception ignored) {
                        }
                        out.add(e);
                    }
                    cb.accept(out);
                }).show();
    }

    // ---------- 계약 ----------
    private List<JSONObject> expiringContracts() {
        List<JSONObject> l = new ArrayList<>();
        for (JSONObject c : store.list(store.contracts())) {
            if (c.optString("expire").isEmpty()) continue;
            if (daysLeft(c.optString("expire")) <= 90) l.add(c);
        }
        Collections.sort(l, (a, b) -> daysLeft(a.optString("expire")) - daysLeft(b.optString("expire")));
        return l;
    }

    private String amountLabel(String kind) {
        return "CMS".equals(kind) ? "CMS 금액" : "렌탈".equals(kind) ? "월 렌탈료" : "판매금액";
    }

    private View contractRow(final JSONObject c) {
        LinearLayout card = Ui.card(this);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, c.optString("client"), 16, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!c.optString("expire").isEmpty()) {
            int d = daysLeft(c.optString("expire"));
            boolean warn = d <= 90;
            head.addView(Ui.chip(this, dday(d), warn ? Color.rgb(253, 236, 234) : Ui.FIELD, warn ? Ui.RED : Ui.GRAY));
        }
        card.addView(head);
        JSONArray eq = c.optJSONArray("equip");
        int n = eq == null ? 0 : eq.length();
        card.addView(Ui.text(this, c.optString("kind") + (c.optLong("amount") > 0 ? " " + won(c.optLong("amount")) : "") + "  ·  장비 " + n + "대  ·  "
                + (c.optBoolean("installed") ? "설치 완료" : "설치 대기") + "  ·  " + store.userName(c.optInt("who")), 12, Ui.GRAY, false), Ui.lp(this, 4));
        if (!c.optString("expire").isEmpty()) card.addView(Ui.text(this, "만료일 " + c.optString("expire"), 12, Ui.GRAY, false), Ui.lp(this, 2));
        final int id = c.optInt("id");
        card.setOnClickListener(v -> showContract(id));
        return card;
    }

    private void renderContracts() {
        content.addView(title("계약 관리"));
        content.addView(Ui.text(this, "올린 계약을 눌러 장비를 추가하거나 '설치 완료'를 처리해요. 오른쪽 아래 + 로 새 계약을 올립니다.", 13, Ui.GRAY, false));
        List<JSONObject> exp = expiringContracts();
        if (!exp.isEmpty()) {
            content.addView(Ui.text(this, "만료가 가까운 계약 (3개월 이내)", 17, Ui.RED, true), Ui.lp(this, 20));
            for (JSONObject c : exp) content.addView(contractRow(c), Ui.lp(this, 8));
        }
        content.addView(Ui.text(this, "전체 계약", 17, Ui.INK, true), Ui.lp(this, 22));
        List<JSONObject> all = store.list(store.contracts());
        Collections.sort(all, (a, b) -> b.optString("created").compareTo(a.optString("created")));
        if (all.isEmpty()) content.addView(Ui.text(this, "아직 올린 계약이 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 10));
        for (JSONObject c : all) content.addView(contractRow(c), Ui.lp(this, 8));
    }

    private void showContract(final int id) {
        final JSONObject c = store.byId("contracts", id);
        if (c == null) return;
        LinearLayout l = form();
        addLine(l, "가맹점", c.optString("client"));
        addLine(l, "구분", c.optString("kind"));
        if (c.optLong("amount") > 0) addLine(l, amountLabel(c.optString("kind")), won(c.optLong("amount")));
        addLine(l, "계약일", c.optString("date"));
        if (!c.optString("expire").isEmpty()) addLine(l, "만료일", c.optString("expire") + "  (" + dday(daysLeft(c.optString("expire"))) + ")");
        String link = c.optString("linkType") + (c.optString("company").isEmpty() ? "" : " " + c.optString("company"));
        addLine(l, "결제 연동", link.trim());
        addLine(l, "담당", store.userName(c.optInt("who")));
        addLine(l, "상태", c.optBoolean("installed") ? "설치 완료 " + c.optString("installedAt") : "설치 대기");
        JSONArray eq = c.optJSONArray("equip");
        l.addView(Ui.text(this, "나간 장비", 14, Ui.NAVY, true), Ui.lp(this, 12));
        if (eq == null || eq.length() == 0) l.addView(Ui.text(this, "없음", 14, Ui.GRAY, false), Ui.lp(this, 4));
        for (int i = 0; eq != null && i < eq.length(); i++) {
            JSONObject e = eq.optJSONObject(i);
            l.addView(Ui.text(this, "· " + e.optString("model") + (e.optString("serial").isEmpty() ? "" : "  /  " + e.optString("serial")), 14, Ui.INK, false), Ui.lp(this, 4));
        }
        addLine(l, "장비 메모", c.optString("memo"));
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("계약 정보").setView(sv)
                .setNegativeButton("닫기", null)
                .setNeutralButton("장비 추가", (d, w) -> pickStock(list -> {
                    addEquipToContract(c, list);
                    showContract(id);
                }));
        if (!c.optBoolean("installed")) {
            b.setPositiveButton("설치 완료", (d, w) -> new AlertDialog.Builder(this).setTitle("설치 완료 처리")
                    .setMessage("나간 장비가 재고에서 빠집니다. 설치를 마쳤나요?")
                    .setNegativeButton("아니요", null).setPositiveButton("설치 완료", (d2, w2) -> {
                        markInstalled(c);
                        render();
                        Ui.toast(this, "설치 완료로 처리했어요.");
                    }).show());
        }
        b.show();
    }

    private void markInstalled(JSONObject c) {
        try {
            c.put("installed", true);
            c.put("installedAt", todayStr());
            JSONArray eq = c.optJSONArray("equip");
            for (int i = 0; eq != null && i < eq.length(); i++) {
                JSONObject r = store.byId("stock", eq.optJSONObject(i).optInt("sid"));
                if (r != null) r.put("status", "설치완료");
            }
        } catch (Exception ignored) {
        }
        store.save();
    }

    private void addEquipToContract(JSONObject c, List<JSONObject> list) {
        try {
            JSONArray eq = c.optJSONArray("equip");
            if (eq == null) {
                eq = new JSONArray();
                c.put("equip", eq);
            }
            for (JSONObject e : list) {
                int sid = e.optInt("sid");
                if (sid != 0) {
                    JSONObject row = store.byId("stock", sid);
                    if (row != null) {
                        JSONObject used = store.takeStock(row, c.optInt("merchant"), c.optInt("id"));
                        if (c.optBoolean("installed")) used.put("status", "설치완료");
                        e.put("sid", used.optInt("id"));
                    }
                }
                eq.put(e);
            }
            c.put("qty", Math.max(1, eq.length()));
        } catch (Exception ignored) {
        }
        store.save();
    }

    private void showAddContract() {
        final JSONObject[] mer = new JSONObject[1];
        final List<JSONObject> equip = new ArrayList<>();
        LinearLayout f = form();
        final TextView merInfo = Ui.text(this, "가맹점을 먼저 선택해 주세요.", 14, Ui.GRAY, false);
        Button pick = Ui.button(this, "가맹점 조회·선택", Ui.TINT, Ui.DEEP);
        pick.setOnClickListener(v -> pickMerchant(m -> {
            mer[0] = m;
            merInfo.setText(merchantInfo(m));
            merInfo.setTextColor(Ui.INK);
        }));
        f.addView(pick);
        f.addView(merInfo, Ui.lp(this, 6));
        final Spinner link = spinner(new String[]{"VAN사", "PG사"});
        final EditText company = Ui.field(this, "VAN사·PG사 업체명 (예: 다우데이타)", false);
        final Spinner kind = spinner(new String[]{"판매", "CMS", "렌탈"});
        final EditText amount = Ui.field(this, "판매금액 (원)", false);
        amount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        kind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                amount.setHint(amountLabel((String) kind.getSelectedItem()) + " (원)");
            }

            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
        f.addView(Ui.text(this, "결제 연동", 13, Ui.GRAY, false), Ui.lp(this, 10));
        f.addView(link);
        f.addView(company);
        f.addView(Ui.text(this, "구분 (판매 / CMS / 렌탈)", 13, Ui.GRAY, false), Ui.lp(this, 10));
        f.addView(kind);
        f.addView(amount);

        f.addView(Ui.text(this, "나가는 장비", 13, Ui.GRAY, false), Ui.lp(this, 12));
        final LinearLayout eqBox = new LinearLayout(this);
        eqBox.setOrientation(LinearLayout.VERTICAL);
        f.addView(eqBox);
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            eqBox.removeAllViews();
            if (equip.isEmpty()) eqBox.addView(Ui.text(this, "아직 고른 장비가 없어요.", 13, Ui.GRAY, false));
            for (final JSONObject e : equip) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(Ui.text(this, e.optString("model") + (e.optString("serial").isEmpty() ? "" : "  /  " + e.optString("serial")), 14, Ui.INK, false),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView minus = Ui.text(this, "−", 22, Ui.RED, true);
                minus.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
                minus.setOnClickListener(v -> {
                    equip.remove(e);
                    refresh[0].run();
                });
                row.addView(minus);
                eqBox.addView(row, Ui.lp(this, 4));
            }
        };
        refresh[0].run();
        Button plus = Ui.button(this, "+  장비 추가 (재고에서 고르기)", Ui.FIELD, Ui.INK);
        plus.setOnClickListener(v -> pickStock(list -> {
            for (JSONObject e : list) {
                boolean dup = false;
                for (JSONObject x : equip) if (x.optInt("sid") != 0 && x.optInt("sid") == e.optInt("sid")) dup = true;
                if (!dup) equip.add(e);
            }
            refresh[0].run();
        }));
        f.addView(plus, Ui.lp(this, 6));
        final EditText memo = Ui.field(this, "장비 메모 (선택)", false);
        memo.setSingleLine(false);
        f.addView(memo, Ui.lp(this, 6));
        final EditText date = Ui.field(this, "계약일 (예: 2026-10-23)", false);
        date.setText(todayStr());
        final EditText expire = Ui.field(this, "계약 만료일 (필수, 예: 2029-10-23)", false);
        f.addView(date, Ui.lp(this, 10));
        f.addView(expire);
        LinearLayout quick = new LinearLayout(this);
        int[] ms = {12, 24, 36};
        for (final int m : ms) {
            quick.addView(pill("+" + m + "개월", false, () -> expire.setText(addMonths(date.getText().toString().trim(), m))));
        }
        f.addView(quick, Ui.lp(this, 8));
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("신규 계약 올리기").setView(sv)
                .setNegativeButton("취소", null).setPositiveButton("올리기", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String dt = date.getText().toString().trim();
            String ex = expire.getText().toString().trim();
            if (mer[0] == null) {
                Ui.toast(this, "가맹점을 선택해 주세요.");
                return;
            }
            if (!dt.matches("\\d{4}-\\d{2}-\\d{2}") || !ex.matches("\\d{4}-\\d{2}-\\d{2}")) {
                Ui.toast(this, "계약일과 만료일을 2026-10-23 형식으로 적어 주세요.");
                return;
            }
            long amt = 0;
            try {
                amt = Long.parseLong(amount.getText().toString().trim());
            } catch (Exception ignored) {
            }
            JSONObject c = store.create("contracts");
            try {
                c.put("date", dt);
                c.put("who", myId());
                c.put("merchant", mer[0].optInt("id"));
                c.put("client", mer[0].optString("name"));
                c.put("linkType", (String) link.getSelectedItem());
                c.put("company", company.getText().toString().trim());
                c.put("kind", (String) kind.getSelectedItem());
                c.put("amount", amt);
                c.put("memo", memo.getText().toString().trim());
                c.put("expire", ex);
                c.put("installed", false);
                c.put("equip", new JSONArray());
                c.put("qty", 1);
            } catch (Exception ignored) {
            }
            addEquipToContract(c, equip);
            dlg.dismiss();
            Ui.toast(this, "신규 계약을 올렸어요.");
            render();
        });
    }

    // ---------- 채팅 ----------
    private long lastTs(int room) {
        long t = 0;
        for (JSONObject m : store.list(store.arr("msgs"))) if (m.optInt("room") == room && m.optLong("ts") > t) t = m.optLong("ts");
        return t;
    }

    private void renderChat() {
        if (chatRoom != 0 && store.byId("rooms", chatRoom) != null) {
            renderRoom();
            return;
        }
        chatRoom = 0;
        content.addView(title("채팅"));
        Button nb = Ui.button(this, "+  새 채팅방 만들기", Ui.GOLD, Ui.DARK);
        nb.setOnClickListener(v -> showAddRoom());
        content.addView(nb, Ui.lp(this, 8));
        List<JSONObject> rooms = store.list(store.arr("rooms"));
        if (rooms.isEmpty()) {
            content.addView(Ui.text(this, "아직 채팅방이 없어요. 위 버튼으로 원하는 방을 만들거나, 아래 버튼으로 기본 방을 한 번에 만들 수 있어요.", 14, Ui.GRAY, false), Ui.lp(this, 16));
            Button def = Ui.button(this, "기본 채팅방 만들기 (전체방·서류접수·입금확인·신규계약·AS설치·공지)", Ui.FIELD, Ui.INK);
            def.setOnClickListener(v -> {
                for (String n : new String[]{"전체방", "서류접수", "입금확인", "신규계약", "AS·설치", "공지"}) {
                    JSONObject r = store.create("rooms");
                    try {
                        r.put("name", n);
                        r.put("by", myId());
                    } catch (Exception ignored) {
                    }
                }
                store.save();
                render();
            });
            content.addView(def, Ui.lp(this, 10));
            return;
        }
        final java.util.Map<Integer, Long> last = new java.util.HashMap<>();
        for (JSONObject r : rooms) last.put(r.optInt("id"), lastTs(r.optInt("id")));
        Collections.sort(rooms, (a, b) -> {
            long x = last.get(a.optInt("id")), y = last.get(b.optInt("id"));
            return x == y ? a.optString("created").compareTo(b.optString("created")) : (y > x ? 1 : -1);
        });
        for (final JSONObject r : rooms) {
            LinearLayout row = Ui.cardH(this);
            row.addView(Ui.tile(this, r.optString("name").isEmpty() ? "?" : r.optString("name").substring(0, 1), Ui.TINT, Ui.DEEP));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(Ui.dp(this, 14), 0, 0, 0);
            mid.addView(Ui.text(this, r.optString("name"), 16, Ui.INK, true));
            String prev = "";
            long lt = last.get(r.optInt("id"));
            if (lt > 0) {
                for (JSONObject m : store.list(store.arr("msgs"))) {
                    if (m.optInt("room") == r.optInt("id") && m.optLong("ts") == lt) {
                        prev = store.userName(m.optInt("who")) + ": " + m.optString("text");
                        break;
                    }
                }
            }
            mid.addView(Ui.text(this, prev.isEmpty() ? "아직 대화가 없어요" : prev, 13, Ui.GRAY, false), Ui.lp(this, 2));
            ((TextView) mid.getChildAt(1)).setSingleLine(true);
            row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (lt > 0) row.addView(Ui.text(this, new SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(new Date(lt)), 11, Ui.GRAY, false));
            row.setOnClickListener(v -> {
                chatRoom = r.optInt("id");
                draft = "";
                render();
            });
            content.addView(row, Ui.lp(this, 8));
        }
    }

    private void showAddRoom() {
        LinearLayout f = form();
        final EditText name = Ui.field(this, "채팅방 이름 (예: 거래처 A/S, 서류 접수)", false);
        f.addView(name);
        new AlertDialog.Builder(this).setTitle("새 채팅방").setView(f)
                .setNegativeButton("취소", null)
                .setPositiveButton("만들기", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        Ui.toast(this, "이름을 입력해 주세요.");
                        return;
                    }
                    JSONObject r = store.create("rooms");
                    try {
                        r.put("name", n);
                        r.put("by", myId());
                    } catch (Exception ignored) {
                    }
                    store.save();
                    chatRoom = r.optInt("id");
                    draft = "";
                    render();
                }).show();
    }

    private void renderRoom() {
        final JSONObject room = store.byId("rooms", chatRoom);
        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "‹  채팅", 15, Ui.DEEP, true);
        back.setPadding(0, Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 12));
        back.setOnClickListener(v -> {
            chatRoom = 0;
            render();
        });
        hd.addView(back, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ren = Ui.text(this, "이름 변경", 13, Ui.GRAY, false);
        ren.setOnClickListener(v -> {
            LinearLayout f = form();
            final EditText name = Ui.field(this, "채팅방 이름", false);
            name.setText(room.optString("name"));
            f.addView(name);
            new AlertDialog.Builder(this).setTitle("이름 변경").setView(f).setNegativeButton("취소", null)
                    .setPositiveButton("저장", (d, w) -> {
                        if (name.getText().toString().trim().isEmpty()) return;
                        try {
                            room.put("name", name.getText().toString().trim());
                        } catch (Exception ignored) {
                        }
                        store.save();
                        render();
                    }).show();
        });
        hd.addView(ren);
        content.addView(hd);
        content.addView(Ui.text(this, room.optString("name"), 22, Ui.INK, true));
        msgBox = new LinearLayout(this);
        msgBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(msgBox, Ui.lp(this, 10));
        fillMsgs();
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.CENTER_VERTICAL);
        final EditText input = Ui.field(this, "메시지를 입력하세요", false);
        input.setSingleLine(false);
        input.setMaxLines(4);
        input.setText(draft);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                draft = s.toString();
            }
        });
        Button send = Ui.button(this, "보내기", Ui.GOLD, Ui.DARK);
        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            JSONObject m = store.create("msgs");
            try {
                m.put("room", chatRoom);
                m.put("who", myId());
                m.put("text", t);
                m.put("ts", System.currentTimeMillis());
            } catch (Exception ignored) {
            }
            store.save();
            draft = "";
            input.setText("");
            fillMsgs();
        });
        in.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48));
        sp.leftMargin = Ui.dp(this, 8);
        sp.topMargin = Ui.dp(this, 8);
        in.addView(send, sp);
        content.addView(in, Ui.lp(this, 10));
    }

    private void fillMsgs() {
        if (msgBox == null) return;
        msgBox.removeAllViews();
        List<JSONObject> ms = new ArrayList<>();
        for (JSONObject m : store.list(store.arr("msgs"))) if (m.optInt("room") == chatRoom) ms.add(m);
        Collections.sort(ms, (a, b) -> Long.compare(a.optLong("ts"), b.optLong("ts")));
        if (ms.isEmpty()) msgBox.addView(Ui.text(this, "첫 메시지를 남겨 보세요.", 14, Ui.GRAY, false), Ui.lp(this, 12));
        String lastDay = "";
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.KOREA);
        for (JSONObject m : ms) {
            String day = df.format(new Date(m.optLong("ts")));
            if (!day.equals(lastDay)) {
                lastDay = day;
                TextView dl = Ui.text(this, day, 12, Ui.GRAY, false);
                dl.setGravity(Gravity.CENTER);
                msgBox.addView(dl, Ui.lp(this, 12));
            }
            boolean mine = m.optInt("who") == myId();
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setGravity(mine ? Gravity.END : Gravity.START);
            if (!mine) wrap.addView(Ui.text(this, store.userName(m.optInt("who")), 12, Ui.GRAY, false));
            TextView b = Ui.text(this, m.optString("text"), 15, Ui.INK, false);
            b.setBackground(Ui.round(mine ? Ui.GOLD : Ui.CARD, 16, this));
            b.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            b.setMaxWidth(Ui.dp(this, 280));
            wrap.addView(b, Ui.lp(this, 2));
            TextView tm = Ui.text(this, tf.format(new Date(m.optLong("ts"))), 10, Ui.GRAY, false);
            wrap.addView(tm);
            LinearLayout.LayoutParams lp = Ui.lp(this, 6);
            msgBox.addView(wrap, lp);
        }
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------- 일정 (달력 + 다가오는 일정) ----------
    private void showAddEvent() {
        final JSONObject[] mer = new JSONObject[1];
        LinearLayout f = form();
        final EditText ti = Ui.field(this, "일정 제목 (필수, 예: A마트 AS 방문)", false);
        final EditText dt = Ui.field(this, "날짜 (예: 2026-10-24)", false);
        dt.setText(calSel.isEmpty() ? todayStr() : calSel);
        final EditText tm = Ui.field(this, "시간 (선택, 예: 14:00)", false);
        final EditText place = Ui.field(this, "장소·주소 (가맹점을 고르면 자동으로 들어가요)", false);
        final TextView merInfo = Ui.text(this, "", 13, Ui.INK, false);
        Button pick = Ui.button(this, "가맹점 조회·선택", Ui.TINT, Ui.DEEP);
        pick.setOnClickListener(v -> pickMerchant(m -> {
            mer[0] = m;
            merInfo.setText(merchantInfo(m));
            if (ti.getText().toString().trim().isEmpty()) ti.setText(m.optString("name") + " 방문");
            place.setText(m.optString("addr"));
        }));
        f.addView(ti);
        f.addView(dt);
        f.addView(tm);
        f.addView(pick, Ui.lp(this, 10));
        f.addView(merInfo, Ui.lp(this, 6));
        f.addView(place);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("일정 추가").setView(sv)
                .setNegativeButton("취소", null).setPositiveButton("추가", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = ti.getText().toString().trim();
            String s = dt.getText().toString().trim();
            String hm = tm.getText().toString().trim();
            if (t.isEmpty() || !s.matches("\\d{4}-\\d{2}-\\d{2}")) {
                Ui.toast(this, "제목과 날짜(2026-10-24 형식)를 확인해 주세요.");
                return;
            }
            if (!hm.isEmpty() && !hm.matches("\\d{1,2}:\\d{2}")) {
                Ui.toast(this, "시간은 14:00 처럼 적어 주세요.");
                return;
            }
            JSONObject e = store.create("events");
            try {
                e.put("date", s);
                e.put("title", t);
                e.put("who", myId());
                e.put("time", hm);
                e.put("place", place.getText().toString().trim());
                if (mer[0] != null) e.put("merchant", mer[0].optInt("id"));
            } catch (Exception ignored) {
            }
            store.save();
            calSel = s;
            try {
                calY = Integer.parseInt(s.substring(0, 4));
                calM = Integer.parseInt(s.substring(5, 7));
            } catch (Exception ignored) {
            }
            dlg.dismiss();
            Ui.toast(this, "일정을 남겼어요. 달력에도 표시됩니다.");
            render();
        });
    }

    private View eventCard(final JSONObject e, boolean showDate) {
        LinearLayout r = Ui.card(this);
        r.setBackground(Ui.round(Ui.TINT, 20, this));
        String when = (showDate ? e.optString("date").substring(5).replace('-', '/') + "  " : "") + e.optString("time");
        if (!when.trim().isEmpty()) r.addView(Ui.text(this, when.trim(), 12, Ui.DEEP, true));
        r.addView(Ui.text(this, e.optString("title"), 15, Ui.INK, true), Ui.lp(this, 2));
        JSONObject m = e.optInt("merchant") == 0 ? null : store.byId("merchants", e.optInt("merchant"));
        String where = e.optString("place");
        if (m != null) {
            r.addView(Ui.text(this, m.optString("name") + " · " + gradeLabel(m.optInt("grade", 5))
                    + (m.optString("phone").isEmpty() ? "" : " · " + m.optString("phone")), 12, Ui.GRAY, false), Ui.lp(this, 2));
        }
        if (!where.isEmpty()) r.addView(Ui.text(this, "장소  " + where, 12, Ui.GRAY, false), Ui.lp(this, 2));
        r.addView(Ui.text(this, store.userName(e.optInt("who")), 11, Ui.GRAY, false), Ui.lp(this, 2));
        r.setOnClickListener(v -> showEvent(e));
        return r;
    }

    private void addUpcoming() {
        content.addView(Ui.text(this, "다가오는 일정", 18, Ui.INK, true), Ui.lp(this, 26));
        content.addView(Ui.text(this, "오늘부터 앞으로 어디에 가야 하는지 한눈에 봐요.", 12, Ui.GRAY, false));
        final String today = todayStr();
        List<Object[]> items = new ArrayList<>();
        for (JSONObject e : store.list(store.events())) {
            if (e.optString("date").compareTo(today) >= 0) items.add(new Object[]{e.optString("date") + " " + e.optString("time"), e, "e"});
        }
        for (JSONObject t : store.list(store.tasks())) {
            String due = t.optString("due");
            if (due.length() < 10 || !due.substring(0, 10).matches("\\d{4}-\\d{2}-\\d{2}")) continue;
            if ("완료".equals(t.optString("status")) || due.substring(0, 10).compareTo(today) < 0) continue;
            if (!(can(Perm.TASK_VIEW_ALL) || t.optInt("assignee") == myId())) continue;
            items.add(new Object[]{due.substring(0, 10) + " 99", t, "t"});
        }
        Collections.sort(items, (a, b) -> ((String) a[0]).compareTo((String) b[0]));
        if (items.isEmpty()) content.addView(Ui.text(this, "예정된 일정이 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 10));
        int n = 0;
        for (Object[] it : items) {
            if (++n > 30) break;
            if ("e".equals(it[2])) {
                content.addView(eventCard((JSONObject) it[1], true), Ui.lp(this, 8));
            } else {
                final JSONObject t = (JSONObject) it[1];
                LinearLayout r = Ui.card(this);
                r.addView(Ui.text(this, t.optString("due").substring(5, 10).replace('-', '/') + "  업무", 12, Ui.DEEP, true));
                r.addView(Ui.text(this, "[" + t.optString("type") + "] " + t.optString("title"), 15, Ui.INK, true), Ui.lp(this, 2));
                String w = (t.optString("client").isEmpty() ? "" : t.optString("client")) + (t.optString("addr").isEmpty() ? "" : " · " + t.optString("addr"));
                if (!w.isEmpty()) r.addView(Ui.text(this, w, 12, Ui.GRAY, false), Ui.lp(this, 2));
                r.setOnClickListener(v -> showTask(t.optInt("id")));
                content.addView(r, Ui.lp(this, 8));
            }
        }
    }

    // ---------- 사용설명서 ----------
    private static final String[][] MANUAL = {
            {"처음 시작하기 · 로그인", "1. 앱을 켜면 잠깐 시작 화면이 나오고, 처음에는 로그인 화면이 나옵니다.\n2. 관리자가 등록해 둔 내 휴대폰 번호(예: 01012345678)를 입력하고 '인증번호 받기'를 누릅니다.\n3. 문자로 온 인증번호 6자리를 입력하고 '확인'을 누릅니다.\n4. 한 번 로그인하면 계속 유지됩니다. 다시 묻지 않아요.\n\n※ '등록되지 않은 번호입니다'가 나오면 관리자에게 내 번호 등록을 요청하세요.\n※ 로그아웃은 맨 아래 탭 '전체' → 맨 아래 '로그아웃'입니다. 폰을 바꾸면 같은 번호로 다시 인증하면 됩니다."},
            {"업무 탭", "내게 지시된 업무를 확인하고 처리하는 곳입니다.\n\n■ 화면 구성\n- 맨 위 금색 카드: 남은 업무 수, 새 업무·진행·오늘 완료 수와 진행 막대\n- 계약 만료 알림: 만료가 3개월 안으로 남은 계약이 있으면 카드가 나타납니다. 누르면 계약 관리로 이동합니다.\n- 업무 카드: 종류(설치·AS·영업·서류·카드), 제목, 거래처, 기한, 상태\n\n■ 업무 처리\n1. 카드를 누르면 상세가 열립니다.\n2. 아래 버튼으로 상태를 바꿉니다: 확인함 → 진행중 → 완료(또는 보류).\n3. '완료'를 누르면 완료 메시지를 적을 수 있어요. 모든 처리 기록은 이력으로 남습니다.\n\n■ 업무 지시하기 (업무 지시 권한이 있는 사람)\n- 오른쪽 아래 + 버튼 → 제목, 종류, 거래처, 전화, 주소, 기한, 메모, 담당 직원 입력 후 '지시하기'.\n- '가맹점 조회'를 누르면 등록된 가맹점을 검색해 선택할 수 있고, 거래처·전화·주소가 자동으로 채워집니다.\n- '전체 업무' 보기 권한이 있으면 위쪽 '내 업무 / 전체 업무' 버튼이 보입니다."},
            {"프로젝트 탭", "여러 사람이 오래 진행하는 일을 관리합니다.\n\n1. 오른쪽 아래 + 로 프로젝트를 만듭니다 (제목, 기한, 메모, 책임자).\n2. 프로젝트를 누르면 진행률(%) 슬라이더, '점검 필요' 체크, 메모를 수정할 수 있습니다.\n3. 저장할 때마다 진행 기록이 쌓여서 누가 언제 무엇을 바꿨는지 볼 수 있어요."},
            {"채팅 탭", "직원끼리 대화하는 곳이에요. 원하는 만큼 채팅방을 만들 수 있습니다.\n\n■ 채팅방 만들기\n- 맨 위 '+ 새 채팅방 만들기' → 이름을 적고 '만들기'. (예: 거래처 A/S, 서류 접수, 입금 확인)\n- 처음이라 방이 하나도 없으면 '기본 채팅방 만들기'로 전체방·서류접수·입금확인·신규계약·AS설치·공지 방을 한 번에 만들 수 있어요.\n\n■ 대화하기\n- 방을 누르면 대화가 열립니다. 아래 입력칸에 쓰고 '보내기'.\n- 내 말풍선은 금색 오른쪽, 다른 사람은 회색 왼쪽입니다.\n- 방 안 오른쪽 위 '이름 변경'으로 방 이름을 바꿀 수 있어요.\n- 대화는 서버에 실시간으로 저장되어 모든 직원 폰에 바로 나타납니다.\n\n※ 모든 직원이 모든 채팅방을 볼 수 있습니다. 민감한 개인 이야기는 올리지 마세요."},
            {"일정 탭 (달력)", "한국 달력과 앞으로의 일정을 함께 봅니다.\n\n■ 달력\n- 일요일·공휴일은 빨간색, 토요일은 파란색, 오늘은 금색 원입니다. 공휴일 이름도 표시돼요. (2026~2028년 기준)\n- ‹ › 로 달을 넘기고 '오늘'을 누르면 오늘로 돌아옵니다.\n- 일정이나 업무 기한이 있는 날은 작은 금색 점이 찍혀요.\n- 날짜를 누르면 그날의 일정과 업무가 아래에 나옵니다.\n\n■ 일정 남기기\n1. 오른쪽 아래 + 버튼.\n2. 제목과 날짜를 적고, 시간은 선택입니다.\n3. '가맹점 조회·선택'을 누르면 등록된 가맹점 중에서 찾아 고릅니다 (초성 검색 가능). 고르면 사업자번호·연락처·주소·계약조건·등급이 불러와지고, 장소에는 가맹점 주소가 자동으로 들어갑니다.\n4. '추가'를 누르면 달력에 바로 표시되고, 달력 아래 '다가오는 일정'에도 올라갑니다.\n\n■ 다가오는 일정\n- 오늘부터 앞으로의 일정과 내 업무 기한이 날짜 순으로 나와서, 언제 어디로 가야 하는지 한눈에 확인할 수 있습니다.\n- 가맹점이 연결된 일정을 누르면 가맹점 상세 정보가 열립니다."},
            {"전체 탭", "나머지 모든 기능으로 들어가는 메뉴판입니다.\n\n- 재고: 장비 재고 관리\n- 가맹점: 가맹점 정보 관리\n- 신규 계약 올리기 / 계약 관리: 계약 등록과 설치 완료 처리, 만료 알림\n- 실적: (권한이 있는 사람만 보임) 월별 신규 계약 실적\n- 지난 업무 기록: 완료된 업무 검색\n- 공지사항 / 사용설명서\n- 직원과 권한: (관리자만) 직원 추가·권한 설정\n- 로그아웃"},
            {"재고", "장비를 우리가 직접 입력해서 관리합니다. (전체 → 재고)\n\n■ 재고 추가\n1. 오른쪽 아래 + 버튼.\n2. 모델명을 적고, 시리얼은 한 줄에 하나씩 적습니다. 여러 줄을 붙여 넣으면 장비가 한꺼번에 등록돼요.\n3. 시리얼이 없는 소모품(롤지 등)은 시리얼 대신 '수량'만 적습니다.\n4. 안전재고: 이 수 아래로 내려가면 빨간색으로 '부족해요!'가 표시됩니다.\n\n■ 보기·검색\n- 모델별로 묶여서 '재고 n대'가 보이고, 그 아래에 시리얼이 나열됩니다.\n- 검색창에 모델명·시리얼을 치면 찾아줘요. 'ㅌㅅㅍㅅ' 같은 초성도 됩니다.\n\n■ 수정·삭제\n- 시리얼 줄을 누르면 수정창이 열립니다. '삭제'를 누르면 목록에서 지워져요 (기록은 서버에 남아 복구 가능).\n\n■ 상태가 바뀌는 순서\n재고 → (계약에서 선택하면) 출고중 → (설치 완료 처리하면) 재고 목록에서 사라짐"},
            {"가맹점", "우리 거래처(가맹점) 정보를 관리합니다. (전체 → 가맹점)\n\n■ 등록\n- 오른쪽 아래 + 버튼 → 상호(필수), 사업자번호, 연락처, 주소, 계약조건, 등급 코드, 메모.\n- 계약·일정·업무 지시 화면의 '가맹점 조회'에서 바로 '새 가맹점 등록'도 가능합니다.\n\n■ 등급 코드\n밴피(VAN) 기준입니다.\n0 S · 월 5만원 이상\n1 A · 3만원 이상\n2 B · 1만원 이상\n3 C · 1만원 미만\n4 해지가맹점\n5 신규가맹점\n7 협력사\n\n■ 수정\n- 목록에서 가맹점을 누르면 정보가 보이고, '수정'으로 바꿀 수 있습니다.\n\n■ 검색\n- 상호·사업자번호·전화·주소 어느 것으로든, 초성으로도 검색됩니다."},
            {"신규 계약 올리기", "계약이 되면 바로 올려 주세요. (전체 → 신규 계약 올리기, 또는 계약 관리 화면의 + 버튼)\n\n1. 가맹점 조회·선택: 등록된 가맹점을 고릅니다. 없으면 '새 가맹점 등록'.\n2. 결제 연동: VAN사 또는 PG사, 업체명 (예: 다우데이타).\n3. 구분을 고르면 금액 입력 이름이 바뀝니다.\n   - 판매 → 판매금액\n   - CMS → CMS 금액\n   - 렌탈 → 월 렌탈료\n4. 나가는 장비: '+ 장비 추가'를 눌러 재고에서 체크해 고릅니다. 재고에 없으면 '직접 입력'으로 모델명·시리얼을 적어요. 잘못 고른 장비는 오른쪽 − 로 뺍니다. 장비를 여러 번 + 해서 여러 대를 넣을 수 있어요.\n5. 장비 메모: 설치 조건 등 자유롭게 적습니다.\n6. 계약일과 만료일: 만료일 아래 '+12개월 / +24개월 / +36개월' 버튼을 누르면 계약일 기준으로 자동 계산됩니다.\n7. '올리기'.\n\n※ 올리면 고른 장비는 재고에서 '출고중'이 됩니다."},
            {"계약 관리 · 설치 완료 · 만료 알림", "(전체 → 계약 관리)\n\n■ 계약 보기\n- 위쪽에 '만료가 가까운 계약(3개월 이내)'이 빨간 D-숫자와 함께 먼저 나옵니다. 아래는 전체 계약입니다.\n- 일반 직원은 내가 올린 계약만, 실적 권한이 있는 사람은 전체 계약이 보입니다.\n\n■ 설치 완료\n- 계약을 누르고 '설치 완료'를 누르면 나간 장비가 재고에서 완전히 빠집니다.\n\n■ 장비 추가\n- 같은 가맹점에 장비가 더 나가면 계약을 열어 '장비 추가'를 눌러 모델명·시리얼을 이어서 추가합니다.\n\n■ 만료 알림\n- 만료가 90일 이하로 남은 계약이 있으면 '업무' 탭 위에 알림 카드가 뜨고, 전체 탭에도 숫자가 표시됩니다."},
            {"실적 (권한자 전용)", "(전체 → 실적, 실적 보기 권한이 있는 사람만 보입니다)\n\n- 위 ‹ › 로 월을 바꿉니다.\n- 이번 달 신규 계약 대수, 직원별 막대 그래프, 올라온 계약 목록이 나옵니다.\n- 계약을 누르면 상세 정보와 장비를 볼 수 있어요.\n- 오른쪽 아래 + 로도 신규 계약을 올릴 수 있습니다."},
            {"공지사항 · 직원과 권한", "■ 공지사항 (전체 → 공지사항)\n- 공지 쓰기 권한이 있으면 + 버튼으로 작성합니다. '맨 위에 고정'을 켜면 항상 위에 보여요.\n- 읽지 않은 공지는 ● 표시와 전체 탭 숫자로 알려줍니다. 누르면 읽음 처리돼요.\n\n■ 직원과 권한 (관리자 전용)\n- + 버튼 → 이름, 휴대폰 번호, 권한 묶음을 선택해 직원을 등록합니다. 등록된 번호로만 로그인할 수 있어요.\n- 직원을 누르면 항목별 권한을 켜고 끌 수 있고, '사용 중지'로 퇴사자의 접근을 막습니다.\n- 마지막 관리자는 중지하거나 권한을 끌 수 없습니다.\n\n※ '실적 보기'는 서버에서 강제로 막혀 안전합니다. 나머지 권한은 화면에서 숨기는 방식이에요."},
            {"일일보고 · 실적 보고서", "■ 일일보고 쓰기 (전체 → 일일보고 쓰기)\n- 날짜를 확인하고, 오늘 한 일·특이사항·내일 계획을 적어 '제출하기'를 누릅니다.\n- 그날 완료한 업무와 올린 계약은 자동으로 같이 집계돼요.\n- 같은 날 다시 쓰면 수정본으로 덮어씁니다. 아래에서 내가 낸 보고를 다시 열어 볼 수 있어요.\n\n■ 일일보고 종합 (대표님)\n- 전체 → 일일보고 종합 보기: 날짜별로 직원별 보고가 한 화면에 모이고, 미제출자는 빨간색으로 표시됩니다.\n\n■ 실적 보고서 (본부장 → 대표님)\n- 전체 → 실적 보고서 올리기: 이번 달 실적이 자동 집계되어 채워져 있고, 고쳐서 '대표님께 보고하기'를 누릅니다.\n- 대표님은 같은 메뉴에서 올라온 보고서를 봅니다.\n\n※ 보고서 양식은 정해지면 그 모양에 맞춰 바꿀 수 있어요."},
            {"자주 묻는 질문", "Q. 인증번호 문자가 안 와요.\nA. 번호가 등록되었는지, 폰 신호가 되는지 확인하세요. 하루에 받을 수 있는 횟수가 제한되어 있어 여러 번 시도하면 잠시 막힐 수 있어요.\n\nQ. 폰을 바꿨어요.\nA. 새 폰에 앱을 설치하고 같은 번호로 다시 인증하면 모든 자료가 그대로 나옵니다. 자료는 서버에 저장돼 있어요.\n\nQ. 인터넷이 없을 때도 써도 되나요?\nA. 열어서 보기와 입력은 가능하고, 인터넷이 다시 연결되면 자동으로 서버에 올라갑니다.\n\nQ. 실수로 지웠어요.\nA. 지운 자료도 서버에는 기록이 남아 있으니 관리자에게 말씀해 주세요."},
    };

    private void renderManual() {
        content.addView(title("사용설명서"));
        content.addView(Ui.text(this, "메뉴별로 자세히 설명해 드려요. 궁금한 항목을 눌러 보세요.", 13, Ui.GRAY, false));
        for (final String[] m : MANUAL) {
            LinearLayout row = Ui.cardH(this);
            row.addView(Ui.text(this, m[0], 16, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.text(this, "›", 20, Ui.DEEP, true));
            row.setOnClickListener(v -> {
                TextView t = Ui.text(this, m[1], 15, Ui.INK, false);
                t.setLineSpacing(0, 1.35f);
                t.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
                ScrollView sv = new ScrollView(this);
                sv.addView(t);
                new AlertDialog.Builder(this).setTitle(m[0]).setView(sv).setPositiveButton("닫기", null).show();
            });
            content.addView(row, Ui.lp(this, 8));
        }
    }


    // ======================= v0.6: 일정 삭제·상세, 실적 순위, 일일보고, 실적 보고서 =======================
    private boolean canReport() {
        return can(Perm.REPORT_VIEW) || can(Perm.STAFF_MANAGE);
    }

    private String dailyDate = "";
    private String prMonth = "";

    private void showEvent(final JSONObject e) {
        LinearLayout l = form();
        addLine(l, "일정", e.optString("title"));
        addLine(l, "날짜", e.optString("date") + (e.optString("time").isEmpty() ? "" : "  " + e.optString("time")));
        addLine(l, "장소", e.optString("place"));
        addLine(l, "등록한 사람", store.userName(e.optInt("who")));
        JSONObject m = e.optInt("merchant") == 0 ? null : store.byId("merchants", e.optInt("merchant"));
        if (m != null) {
            l.addView(Ui.text(this, "가맹점 정보", 14, Ui.NAVY, true), Ui.lp(this, 12));
            TextView t = Ui.text(this, merchantInfo(m), 14, Ui.INK, false);
            t.setLineSpacing(0, 1.3f);
            l.addView(t, Ui.lp(this, 4));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("일정 상세").setView(sv).setPositiveButton("닫기", null);
        if (e.optInt("who") == myId() || can(Perm.STAFF_MANAGE)) {
            b.setNeutralButton("삭제", (d, w) -> new AlertDialog.Builder(this).setTitle("일정을 삭제할까요?")
                    .setMessage(e.optString("title") + "\n" + e.optString("date"))
                    .setNegativeButton("취소", null).setPositiveButton("삭제", (d2, w2) -> {
                        try {
                            e.put("deleted", true);
                        } catch (Exception ignored) {
                        }
                        store.save();
                        Ui.toast(this, "일정을 삭제했어요.");
                        render();
                    }).show());
        }
        b.show();
    }

    // ---------- 실적 ----------
    /** 월별 직원 통계: {신규 대수, 판매 건, CMS 건, 렌탈 건, 판매금액 합} */
    private java.util.Map<Integer, long[]> perfStats(String ym, boolean mineOnly) {
        java.util.Map<Integer, long[]> st = new java.util.HashMap<>();
        for (JSONObject c : store.list(store.contracts())) {
            if (!c.optString("date").startsWith(ym)) continue;
            if (mineOnly && c.optInt("who") != myId()) continue;
            long[] a = st.get(c.optInt("who"));
            if (a == null) {
                a = new long[5];
                st.put(c.optInt("who"), a);
            }
            a[0] += c.optInt("qty", 1);
            String k = c.optString("kind");
            if ("판매".equals(k)) {
                a[1]++;
                a[4] += c.optLong("amount");
            } else if ("CMS".equals(k)) a[2]++;
            else if ("렌탈".equals(k)) a[3]++;
        }
        return st;
    }

    private List<JSONObject> rankedStaff(final java.util.Map<Integer, long[]> st) {
        List<JSONObject> staff = activeStaff();
        Collections.sort(staff, (a, b) -> {
            long[] x = st.get(a.optInt("id")), y = st.get(b.optInt("id"));
            long cx = x == null ? 0 : x[0], cy = y == null ? 0 : y[0];
            if (cx != cy) return cy > cx ? 1 : -1;
            long ax = x == null ? 0 : x[4], ay = y == null ? 0 : y[4];
            return ay > ax ? 1 : ay < ax ? -1 : 0;
        });
        return staff;
    }

    private void monthNav(final Runnable onChange) {
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
            onChange.run();
        });
        TextView nxt = Ui.text(this, "›", 24, Ui.DEEP, true);
        nxt.setPadding(Ui.dp(this, 16), 0, 0, 0);
        nxt.setOnClickListener(v -> {
            perfM++;
            if (perfM > 12) {
                perfM = 1;
                perfY++;
            }
            onChange.run();
        });
        nav2.addView(prev);
        nav2.addView(Ui.text(this, perfY + "년 " + perfM + "월", 18, Ui.INK, true));
        nav2.addView(nxt);
        content.addView(nav2, Ui.lp(this, 6));
    }

    private void renderPerf(final boolean mine) {
        Calendar now = Calendar.getInstance();
        if (perfY == 0) {
            perfY = now.get(Calendar.YEAR);
            perfM = now.get(Calendar.MONTH) + 1;
        }
        content.addView(title(mine ? "내 실적" : "전체 실적"));
        content.addView(Ui.text(this, mine ? "나만 볼 수 있는 내 실적이에요." : "직원별 순위는 신규 계약 대수 순, 같으면 판매금액 순이에요.", 13, Ui.GRAY, false));
        monthNav(() -> render());
        String key = String.format(Locale.KOREA, "%04d-%02d", perfY, perfM);
        java.util.Map<Integer, long[]> st = perfStats(key, mine);
        long total = 0, sales = 0;
        for (long[] a : st.values()) {
            total += a[0];
            sales += a[4];
        }
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.round(Ui.GOLD, 24, this));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        hero.addView(Ui.text(this, mine ? "이번 달 내 신규 계약" : "이번 달 신규 계약", 14, Color.argb(200, 31, 27, 18), false));
        hero.addView(Ui.text(this, total + "대", 34, Ui.DARK, true));
        hero.addView(Ui.text(this, "판매금액 합계 " + won(sales), 13, Color.argb(200, 31, 27, 18), false), Ui.lp(this, 4));
        content.addView(hero, Ui.lp(this, 14));
        if (!mine) {
            content.addView(Ui.text(this, "직원별 순위", 18, Ui.INK, true), Ui.lp(this, 24));
            List<JSONObject> staff = rankedStaff(st);
            long max = 1;
            for (long[] a : st.values()) if (a[0] > max) max = a[0];
            int rank = 0;
            for (JSONObject u : staff) {
                rank++;
                long[] a = st.get(u.optInt("id"));
                long n = a == null ? 0 : a[0];
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView rk = Ui.text(this, String.valueOf(rank), 15, rank <= 3 && n > 0 ? Ui.DEEP : Ui.GRAY, true);
                rk.setGravity(Gravity.CENTER);
                row.addView(rk, new LinearLayout.LayoutParams(Ui.dp(this, 28), ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(Ui.text(this, u.optString("name"), 14, Ui.INK, rank <= 3 && n > 0), new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(bar((int) (n * 100 / max), Ui.GOLD, Ui.FIELD, 14), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView cnt = Ui.text(this, String.valueOf(n), 14, Ui.DEEP, true);
                cnt.setGravity(Gravity.END);
                row.addView(cnt, new LinearLayout.LayoutParams(Ui.dp(this, 36), ViewGroup.LayoutParams.WRAP_CONTENT));
                content.addView(row, Ui.lp(this, 10));
                if (a != null) {
                    TextView d = Ui.text(this, "판매 " + a[1] + " · CMS " + a[2] + " · 렌탈 " + a[3] + (a[4] > 0 ? " · 판매금액 " + won(a[4]) : ""), 11, Ui.GRAY, false);
                    d.setPadding(Ui.dp(this, 92), 0, 0, 0);
                    content.addView(d);
                }
            }
        }
        content.addView(Ui.text(this, "올라온 계약", 18, Ui.INK, true), Ui.lp(this, 24));
        List<JSONObject> month = new ArrayList<>();
        for (JSONObject c : store.list(store.contracts())) {
            if (!c.optString("date").startsWith(key)) continue;
            if (mine && c.optInt("who") != myId()) continue;
            month.add(c);
        }
        Collections.sort(month, (a, b) -> b.optString("created").compareTo(a.optString("created")));
        if (month.isEmpty()) content.addView(Ui.text(this, "이 달에 올라온 계약이 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 10));
        for (JSONObject c : month) content.addView(contractRow(c), Ui.lp(this, 8));
    }

    // ---------- 일일보고 ----------
    private String dailyAuto(String date, int who) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (JSONObject t : store.list(store.tasks())) {
            if (t.optInt("assignee") == who && t.optString("doneAt").startsWith(date)) {
                b.append("· 완료 업무: [").append(t.optString("type")).append("] ").append(t.optString("title"));
                if (!t.optString("client").isEmpty()) b.append(" (").append(t.optString("client")).append(")");
                b.append("\n");
                n++;
            }
        }
        int cn = 0;
        for (JSONObject c : store.list(store.contracts())) {
            if (c.optInt("who") == who && date.equals(c.optString("date"))) {
                b.append("· 신규 계약: ").append(c.optString("client")).append(" / ").append(c.optString("kind")).append("\n");
                cn++;
            }
        }
        if (n == 0 && cn == 0) return "(자동 집계: 오늘 완료한 업무·올린 계약 없음)";
        return "(자동 집계) 완료 업무 " + n + "건 · 신규 계약 " + cn + "건\n" + b.toString().trim();
    }

    private JSONObject findReport(String kind, int who, String date) {
        for (JSONObject r : store.list(store.arr("reports"))) {
            if (kind.equals(r.optString("kind")) && r.optInt("who") == who && date.equals(r.optString("date"))) return r;
        }
        return null;
    }

    private void dateNav(final Runnable onChange) {
        LinearLayout nav2 = new LinearLayout(this);
        nav2.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = Ui.text(this, "‹", 24, Ui.DEEP, true);
        prev.setPadding(0, 0, Ui.dp(this, 16), 0);
        prev.setOnClickListener(v -> {
            dailyDate = addDays(dailyDate, -1);
            onChange.run();
        });
        TextView nxt = Ui.text(this, "›", 24, Ui.DEEP, true);
        nxt.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        nxt.setOnClickListener(v -> {
            dailyDate = addDays(dailyDate, 1);
            onChange.run();
        });
        TextView td = Ui.text(this, "오늘", 14, Ui.DEEP, true);
        td.setOnClickListener(v -> {
            dailyDate = todayStr();
            onChange.run();
        });
        nav2.addView(prev);
        nav2.addView(Ui.text(this, dailyDate, 18, Ui.INK, true));
        nav2.addView(nxt);
        nav2.addView(td);
        content.addView(nav2, Ui.lp(this, 6));
    }

    private static String addDays(String ymd, int d) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(ymd));
            c.add(Calendar.DAY_OF_MONTH, d);
            return f.format(c.getTime());
        } catch (Exception e) {
            return todayStr();
        }
    }

    private void renderDaily() {
        if (dailyDate.isEmpty()) dailyDate = todayStr();
        content.addView(title("일일보고 쓰기"));
        content.addView(Ui.text(this, "오늘 한 일과 특이사항을 적어 제출해요. 완료한 업무와 올린 계약은 자동으로 같이 들어갑니다. (양식이 정해지면 맞춰 바꿀게요)", 13, Ui.GRAY, false));
        dateNav(() -> render());
        LinearLayout auto = Ui.card(this);
        auto.addView(Ui.text(this, dailyAuto(dailyDate, myId()), 13, Ui.INK, false));
        content.addView(auto, Ui.lp(this, 12));
        final JSONObject old = findReport("daily", myId(), dailyDate);
        final EditText body = Ui.field(this, "오늘 한 일 · 특이사항 · 내일 계획", false);
        body.setSingleLine(false);
        body.setMinLines(6);
        body.setGravity(Gravity.TOP);
        if (old != null) body.setText(old.optString("text"));
        content.addView(body, Ui.lp(this, 12));
        Button save = Ui.button(this, old == null ? "제출하기" : "수정해서 다시 제출", Ui.GOLD, Ui.DARK);
        save.setOnClickListener(v -> {
            String t = body.getText().toString().trim();
            if (t.isEmpty()) {
                Ui.toast(this, "내용을 적어 주세요.");
                return;
            }
            JSONObject r = old != null ? old : store.create("reports");
            try {
                r.put("kind", "daily");
                r.put("date", dailyDate);
                r.put("who", myId());
                r.put("text", t);
                r.put("auto", dailyAuto(dailyDate, myId()));
                r.put("ts", System.currentTimeMillis());
            } catch (Exception ignored) {
            }
            store.save();
            Ui.toast(this, "일일보고를 제출했어요.");
            render();
        });
        content.addView(save, Ui.lp(this, 10));
        content.addView(Ui.text(this, "내가 낸 보고", 17, Ui.INK, true), Ui.lp(this, 24));
        List<JSONObject> mine = new ArrayList<>();
        for (JSONObject r : store.list(store.arr("reports"))) if ("daily".equals(r.optString("kind")) && r.optInt("who") == myId()) mine.add(r);
        Collections.sort(mine, (a, b) -> b.optString("date").compareTo(a.optString("date")));
        if (mine.isEmpty()) content.addView(Ui.text(this, "아직 낸 보고가 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 8));
        int n = 0;
        for (final JSONObject r : mine) {
            if (++n > 14) break;
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, r.optString("date"), 13, Ui.DEEP, true));
            c.addView(Ui.text(this, r.optString("text"), 14, Ui.INK, false), Ui.lp(this, 2));
            c.setOnClickListener(v -> {
                dailyDate = r.optString("date");
                render();
            });
            content.addView(c, Ui.lp(this, 8));
        }
    }

    /** 대표님: 하루치 일일보고를 직원별로 종합해서 본다. */
    private void renderReportView() {
        if (dailyDate.isEmpty()) dailyDate = todayStr();
        content.addView(title("일일보고 종합"));
        content.addView(Ui.text(this, "직원별 일일보고를 한 화면에 모아 봐요.", 13, Ui.GRAY, false));
        dateNav(() -> render());
        List<JSONObject> staff = activeStaff();
        int done = 0;
        for (JSONObject u : staff) if (findReport("daily", u.optInt("id"), dailyDate) != null) done++;
        content.addView(Ui.text(this, "제출 " + done + " / " + staff.size() + "명", 16, Ui.DEEP, true), Ui.lp(this, 12));
        for (JSONObject u : staff) {
            JSONObject r = findReport("daily", u.optInt("id"), dailyDate);
            LinearLayout card = Ui.card(this);
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(Ui.text(this, u.optString("name"), 16, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            head.addView(Ui.chip(this, r == null ? "미제출" : "제출", r == null ? Color.rgb(253, 236, 234) : Ui.TINT, r == null ? Ui.RED : Ui.DEEP));
            card.addView(head);
            if (r != null) {
                if (!r.optString("auto").isEmpty()) card.addView(Ui.text(this, r.optString("auto"), 12, Ui.GRAY, false), Ui.lp(this, 6));
                card.addView(Ui.text(this, r.optString("text"), 14, Ui.INK, false), Ui.lp(this, 6));
            }
            content.addView(card, Ui.lp(this, 8));
        }
    }

    // ---------- 실적 보고서 (본부장 → 대표님) ----------
    private String perfSnapshot(String ym) {
        java.util.Map<Integer, long[]> st = perfStats(ym, false);
        StringBuilder b = new StringBuilder();
        long total = 0;
        for (long[] a : st.values()) total += a[0];
        b.append(ym).append(" 신규 계약 합계 ").append(total).append("대\n");
        int rank = 0;
        for (JSONObject u : rankedStaff(st)) {
            rank++;
            long[] a = st.get(u.optInt("id"));
            if (a == null) a = new long[5];
            b.append(rank).append("위 ").append(u.optString("name")).append(" - ").append(a[0]).append("대 (판매 ").append(a[1])
                    .append(" · CMS ").append(a[2]).append(" · 렌탈 ").append(a[3]).append(")");
            if (a[4] > 0) b.append(" 판매금액 ").append(won(a[4]));
            b.append("\n");
        }
        return b.toString().trim();
    }

    private void renderPerfReport() {
        Calendar now = Calendar.getInstance();
        if (perfY == 0) {
            perfY = now.get(Calendar.YEAR);
            perfM = now.get(Calendar.MONTH) + 1;
        }
        final String ym = String.format(Locale.KOREA, "%04d-%02d", perfY, perfM);
        content.addView(title("실적 보고서"));
        monthNav(() -> render());
        if (canPerf()) {
            content.addView(Ui.text(this, "본부장이 이달 실적을 최종 정리해서 대표님께 올리는 곳이에요. 아래 내용은 자동 집계이며 고쳐서 올릴 수 있어요. (보고서 양식이 정해지면 맞춰 바꿀게요)", 13, Ui.GRAY, false), Ui.lp(this, 6));
            final JSONObject old = findReport("perf", myId(), ym);
            final EditText body = Ui.field(this, "실적 보고 내용", false);
            body.setSingleLine(false);
            body.setMinLines(8);
            body.setGravity(Gravity.TOP);
            body.setText(old != null ? old.optString("text") : perfSnapshot(ym));
            content.addView(body, Ui.lp(this, 10));
            Button save = Ui.button(this, old == null ? "대표님께 보고하기" : "수정해서 다시 보고", Ui.GOLD, Ui.DARK);
            save.setOnClickListener(v -> {
                String t = body.getText().toString().trim();
                if (t.isEmpty()) return;
                JSONObject r = old != null ? old : store.create("reports");
                try {
                    r.put("kind", "perf");
                    r.put("date", ym);
                    r.put("who", myId());
                    r.put("text", t);
                    r.put("ts", System.currentTimeMillis());
                } catch (Exception ignored) {
                }
                store.save();
                Ui.toast(this, "대표님께 보고했어요.");
                render();
            });
            content.addView(save, Ui.lp(this, 10));
        }
        if (canReport()) {
            content.addView(Ui.text(this, "올라온 실적 보고서", 17, Ui.INK, true), Ui.lp(this, 24));
            boolean any = false;
            for (JSONObject r : store.list(store.arr("reports"))) {
                if (!"perf".equals(r.optString("kind")) || !ym.equals(r.optString("date"))) continue;
                any = true;
                LinearLayout c = Ui.card(this);
                c.addView(Ui.text(this, store.userName(r.optInt("who")) + " · " + new SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(new Date(r.optLong("ts"))), 12, Ui.DEEP, true));
                c.addView(Ui.text(this, r.optString("text"), 14, Ui.INK, false), Ui.lp(this, 4));
                content.addView(c, Ui.lp(this, 8));
            }
            if (!any) content.addView(Ui.text(this, "이 달에 올라온 보고서가 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 8));
        }
    }

}
