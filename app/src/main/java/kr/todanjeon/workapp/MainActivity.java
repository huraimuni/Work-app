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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
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
        setupNotify();
        store.attach(store.sessionId(), store.cachedPerf(), store.cachedReport(), this::onRemote);
        me = store.sessionUser();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::autoBackup, 25000);
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
            if (inRoom) fillMsgs();
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
        if (me != null && inRoom) showRoom();
        else if (me != null) buildMain();
        else if (lastAuth != null) lastAuth.run();
    }

    @Override
    public void onBackPressed() {
        if (inRoom) {
            leaveRoom();
            return;
        }
        if (me != null && sub != null) {
            sub = null;
            render();
            return;
        }
        super.onBackPressed();
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
        setContentView(authScreen("오늘도 반갑습니다.", "등록된 휴대폰 번호로 문자 인증을 해 주세요.\n(한 번 로그인하면 계속 유지됩니다)\n\n나인패밀리 " + appVersion(), card));
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
                    store.setReportCap(report);
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

    /** 0.7.0: 관리자도 '일일보고·실적보고 종합 보기'를 따로 켜고 끌 수 있게 됐다. 기존 관리자에게는 한 번만 켜 준다. */
    private void migrateAdminReport() {
        try {
            if (Store.has(me, Perm.STAFF_MANAGE) && !me.optBoolean("migR")) {
                JSONArray p = me.optJSONArray("perms");
                if (p == null) {
                    p = new JSONArray();
                    me.put("perms", p);
                }
                if (!Store.has(me, Perm.REPORT_VIEW)) p.put(Perm.REPORT_VIEW);
                me.put("migR", true);
                store.save();
            }
        } catch (Exception ignored) {
        }
    }

    private void buildMain() {
        migrateAdminReport();
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
        View av = avatarView(myId(), 44);
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
        else if ("about".equals(k)) renderAbout();
        else if ("sms".equals(k)) renderSms();
        else if ("deposit".equals(k)) renderDeposits();
        else if ("contacts".equals(k)) renderContacts();
        else if ("library".equals(k)) renderLibrary();
        else if ("profit".equals(k) && canPerf()) renderProfit();
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
        } else if ("deposit".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("입금 올리기");
            fab.setOnClickListener(v -> showAddDeposit());
        } else if ("contacts".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("연락처 추가");
            fab.setOnClickListener(v -> showEditContact(null));
        } else if ("library".equals(k)) {
            fab.setVisibility(View.VISIBLE);
            fab.setContentDescription("자료 올리기");
            fab.setOnClickListener(v -> showEditLibrary(null));
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
                    String tp = TYPES[type.getSelectedItemPosition()];
                    if (tp.contains("서류")) {
                        postToRoom("서류접수", "📄 서류 업무 · " + ti + (client.getText().toString().trim().isEmpty() ? "" : " · " + client.getText().toString().trim())
                                + "\n담당 " + store.userName(assignee) + " · 지시 " + store.userName(myId()) + (due.getText().toString().trim().isEmpty() ? "" : " · 기한 " + due.getText().toString().trim()));
                    }
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
    /** 전체 탭에서 검색되는 기능 한 줄. */
    private static final class Entry {
        final String section, label, words;
        String badge = "";
        final Runnable go;

        Entry(String section, String label, String words, Runnable go) {
            this.section = section;
            this.label = label;
            this.words = words;
            this.go = go;
        }
    }

    private void goSub(final String s) {
        sub = s;
        chatRoom = 0;
        render();
    }

    private List<Entry> menuEntries() {
        List<Entry> e = new ArrayList<>();
        e.add(new Entry("바로가기", "업무", "업무 지시 할일 설치 AS 서류", () -> {
            tab = "task";
            sub = null;
            render();
        }));
        e.add(new Entry("바로가기", "프로젝트", "프로젝트 진행률", () -> {
            tab = "project";
            sub = null;
            render();
        }));
        e.add(new Entry("바로가기", "채팅", "채팅 대화 메시지 방 사진 파일 스티커 이모티콘", () -> {
            tab = "chat";
            sub = null;
            render();
        }));
        e.add(new Entry("바로가기", "일정 (달력)", "일정 달력 캘린더 방문 약속", () -> {
            tab = "calendar";
            sub = null;
            render();
        }));
        e.add(new Entry("일", "재고", "재고 장비 바코드 시리얼 단가 입고 출고 스캔", () -> goSub("stock")));
        e.add(new Entry("일", "가맹점", "가맹점 거래처 상호 사업자 등급 삭제", () -> goSub("merchants")));
        e.add(new Entry("일", "가맹점 일괄 가져오기 (엑셀·이카운트)", "가맹점 일괄 가져오기 업로드 엑셀 csv 이카운트 거래처 등록", () -> {
            goSub("merchants");
            startMerchantImport();
        }));
        Entry c = new Entry("일", "계약 관리 (설치 완료·만료 알림)", "계약 설치 완료 만료 알림 장비 추가", () -> goSub("contracts"));
        int ex = expiringContracts().size();
        c.badge = ex > 0 ? "만료 " + ex : "";
        e.add(c);
        e.add(new Entry("일", "신규 계약 올리기", "신규 계약 올리기 CMS 판매 렌탈", () -> showAddContract()));
        e.add(new Entry("일", "입금 확인 (입금 내역 올리기)", "입금 확인 입금 내역 미납금 판매대금 정보변경수수료 입금확인", () -> goSub("deposit")));
        e.add(new Entry("회사", "자료실", "자료실 게시판 양식 서식 계약서 파일 문서 자료 올리기 내려받기", () -> goSub("library")));
        e.add(new Entry("회사", "비상연락망 (눌러서 전화)", "비상연락망 연락처 전화 번호 비상 본사 카드사 밴사", () -> goSub("contacts")));
        e.add(new Entry("일", "단체 문자 보내기 (가맹점)", "단체 문자 sms 발송 가맹점 문자 안내", () -> goSub("sms")));
        e.add(new Entry("일", "내 실적", "내 실적 월별 성장률 계약", () -> goSub("myperf")));
        if (canPerf()) {
            e.add(new Entry("일", "전체 실적·직원별 순위", "전체 실적 직원별 순위 성장률 월별 그래프", () -> goSub("perf")));
            e.add(new Entry("일", "수익 현황 (입고·출고 단가)", "수익 현황 입고 출고 단가 매출 매입 총수익 얼마 벌었", () -> goSub("profit")));
            e.add(new Entry("일", "실적 보고서 올리기 (대표님께)", "실적 보고서 올리기 대표님 본부장", () -> goSub("perfreport")));
        }
        e.add(new Entry("일", "일일보고 쓰기", "일일보고 보고 오늘 한 일", () -> goSub("daily")));
        if (canReport()) {
            e.add(new Entry("일", "일일보고 종합 보기", "일일보고 종합 대표님 직원별", () -> goSub("reportview")));
            if (!canPerf()) e.add(new Entry("일", "실적 보고서 보기", "실적 보고서 보기 대표님", () -> goSub("perfreport")));
        }
        e.add(new Entry("일", "지난 업무 기록", "지난 업무 기록 이력 완료 검색", () -> goSub("history")));
        e.add(new Entry("회사", "사용설명서", "사용설명서 매뉴얼 도움말 사용법", () -> goSub("manual")));
        e.add(new Entry("회사", "버전·업데이트 내역", "버전 업데이트 내역 변경 이력", () -> goSub("about")));
        Entry n = new Entry("회사", "공지사항", "공지사항 공지 알림", () -> goSub("notice"));
        int un = unreadNotices();
        n.badge = un > 0 ? String.valueOf(un) : "";
        e.add(n);
        if (can(Perm.STAFF_MANAGE)) e.add(new Entry("관리자", "데이터 백업 내려받기", "데이터 백업 내려받기 저장 내보내기 json", () -> exportBackup()));
        if (can(Perm.STAFF_MANAGE)) e.add(new Entry("관리자", "가맹점 보호·복원", "가맹점 보호 복원 백업 되돌리기 삭제 복구 휴지통", () -> showSafetyMenu()));
        if (can(Perm.STAFF_MANAGE)) e.add(new Entry("관리자", "직원과 권한", "직원 권한 등록 사용 중지 관리자", () -> goSub("staff")));
        e.add(new Entry("계정", "내 프로필 (사진·상태 메시지)", "프로필 사진 상태 메시지 내 정보", () -> showProfile(myId())));
        e.add(new Entry("계정", "로그아웃", "로그아웃 나가기", () -> new AlertDialog.Builder(this).setTitle("로그아웃")
                .setMessage("로그아웃 할까요?").setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (d, w) -> doLogout()).show()));
        return e;
    }

    private String allQuery = "";

    private void renderAll() {
        content.addView(title("전체"));
        LinearLayout prof = Ui.cardH(this);
        prof.addView(avatarView(myId(), 48));
        prof.setOnClickListener(v -> showProfile(myId()));
        LinearLayout pt = new LinearLayout(this);
        pt.setOrientation(LinearLayout.VERTICAL);
        pt.setPadding(Ui.dp(this, 14), 0, 0, 0);
        pt.addView(Ui.text(this, me.optString("name"), 17, Ui.INK, true));
        pt.addView(Ui.text(this, can(Perm.STAFF_MANAGE) ? "관리자" : "직원", 13, Ui.GRAY, false), Ui.lp(this, 2));
        pt.addView(Ui.text(this, "눌러서 프로필 사진·상태 메시지 바꾸기", 11, Ui.GRAY, false), Ui.lp(this, 2));
        prof.addView(pt);
        content.addView(prof, Ui.lp(this, 8));

        final EditText q = Ui.field(this, "🔍  기능 검색 (예: 재고, ㅈㄱ, 문자, 수익)", false);
        q.setText(allQuery);
        content.addView(q, Ui.lp(this, 10));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        final List<Entry> entries = menuEntries();
        final Runnable fill = () -> {
            list.removeAllViews();
            String s = allQuery.trim();
            if (s.isEmpty()) {
                String lastSec = "";
                for (Entry en : entries) {
                    if ("바로가기".equals(en.section)) continue;
                    if (!en.section.equals(lastSec)) {
                        lastSec = en.section;
                        list.addView(sectionLabel(en.section));
                    }
                    list.addView(menuRow(en.label, en.badge, en.go));
                }
            } else {
                int n = 0;
                for (Entry en : entries) {
                    if (!Kor.match(en.label + " " + en.words, s)) continue;
                    n++;
                    list.addView(menuRow(en.label, en.badge, en.go), Ui.lp(this, 0));
                }
                if (n == 0) list.addView(Ui.text(this, "'" + s + "' 에 맞는 기능이 없어요. 다른 단어로 찾아보세요.", 14, Ui.GRAY, false), Ui.lp(this, 16));
            }
            TextView ver = Ui.text(this, "나인패밀리 " + appVersion() + "  ·  주식회사 나인페이먼츠", 12, Ui.GRAY, false);
            ver.setGravity(Gravity.CENTER);
            list.addView(ver, Ui.lp(this, 28));
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                allQuery = s.toString();
                fill.run();
            }
        });
        fill.run();
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
        AlertDialog.Builder mb = new AlertDialog.Builder(this).setTitle(existing == null ? "가맹점 등록" : "가맹점 정보 수정").setView(sv)
                .setNegativeButton("취소", null).setPositiveButton("저장", null);
        if (existing != null && can(Perm.STAFF_MANAGE)) mb.setNeutralButton("삭제", (d, w) -> confirmDeleteMerchant(existing, cb != null ? cb : x -> render()));
        final AlertDialog dlg = mb.create();
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
        if (can(Perm.STAFF_MANAGE)) {
            TextView hs = Ui.text(this, "🕘 변경 이력 · 되돌리기", 14, Ui.DEEP, true);
            hs.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
            hs.setOnClickListener(v -> showMerchantHistory(m));
            l.addView(hs);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("가맹점 정보").setView(l)
                .setNegativeButton("닫기", null)
                .setPositiveButton("수정", (d, w) -> showEditMerchant(m, x -> render()));
        if (can(Perm.STAFF_MANAGE)) b.setNeutralButton("삭제", (d, w) -> confirmDeleteMerchant(m, x -> render()));
        b.show();
    }

    /** 가맹점 삭제는 최고관리자만. 목록에서만 사라지고 서버 기록·기존 계약 글자는 남는다. */
    private void confirmDeleteMerchant(final JSONObject m, final java.util.function.Consumer<JSONObject> after) {
        if (!can(Perm.STAFF_MANAGE)) {
            Ui.toast(this, "가맹점 삭제는 최고관리자만 할 수 있어요.");
            return;
        }
        int cn = 0, en = 0;
        for (JSONObject c : store.list(store.contracts())) if (c.optInt("merchant") == m.optInt("id")) cn++;
        for (JSONObject e : store.list(store.arr("events"))) if (e.optInt("merchant") == m.optInt("id")) en++;
        new AlertDialog.Builder(this).setTitle("'" + m.optString("name") + "' 삭제할까요?")
                .setMessage("가맹점 목록에서 지워집니다.\n연결된 계약 " + cn + "건·일정 " + en + "건의 기록은 그대로 남고, 서버에도 기록이 보관돼요.")
                .setNegativeButton("취소", null).setPositiveButton("삭제", (d, w) -> {
                    try {
                        m.put("deleted", true);
                    } catch (Exception ignored) {
                    }
                    store.save();
                    Ui.toast(this, "삭제했어요.");
                    after.accept(m);
                }).show();
    }

    private void renderMerchants() {
        content.addView(title("가맹점"));
        content.addView(Ui.text(this, "우리가 관리하는 가맹점 목록이에요. 오른쪽 아래 + 로 새로 등록하고, 눌러서 수정합니다.", 13, Ui.GRAY, false));
        Button imp = Ui.button(this, "📥 엑셀·CSV 로 가맹점 일괄 가져오기 (이카운트 등)", Ui.TINT, Ui.DEEP);
        imp.setOnClickListener(v -> startMerchantImport());
        content.addView(imp, Ui.lp(this, 10));
        if (can(Perm.STAFF_MANAGE)) {
            Button sf = Ui.button(this, "🛡 가맹점 보호·복원 (삭제 복원 · 백업 · 되돌리기)", Ui.FIELD, Ui.INK);
            sf.setOnClickListener(v -> showSafetyMenu());
            content.addView(sf, Ui.lp(this, 8));
        }
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
        if (canPerf()) {
            long asset = 0;
            int noPrice = 0;
            for (JSONObject r : store.list(store.arr("stock"))) {
                if (r.optBoolean("deleted") || !"재고".equals(r.optString("status"))) continue;
                JSONObject cd = store.costDoc(r.optInt("id"), false);
                if (cd == null || cd.optLong("buy") == 0) noPrice += r.optInt("qty", 1);
                else asset += cd.optLong("buy") * r.optInt("qty", 1);
            }
            LinearLayout ac = Ui.card(this);
            ac.addView(Ui.text(this, "보유 재고 자산 (입고 단가 합계)", 13, Ui.GRAY, false));
            ac.addView(Ui.text(this, won(asset), 22, Ui.INK, true), Ui.lp(this, 2));
            if (noPrice > 0) ac.addView(Ui.text(this, "단가 미입력 " + noPrice + "대는 빠져 있어요.", 12, Ui.AMBER, false), Ui.lp(this, 2));
            Button pb = Ui.button(this, "수익 현황 보기", Ui.TINT, Ui.DEEP);
            pb.setOnClickListener(v -> {
                sub = "profit";
                render();
            });
            ac.addView(pb, Ui.lp(this, 8));
            content.addView(ac, Ui.lp(this, 10));
        }
        final EditText q = Ui.field(this, "모델명·시리얼 검색 (초성도 돼요)", false);
        content.addView(q, Ui.lp(this, 10));
        Button scq = Ui.button(this, "바코드로 찾기", Ui.TINT, Ui.DEEP);
        scq.setOnClickListener(v -> scanBarcode(q, false));
        content.addView(scq, Ui.lp(this, 6));
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
        final EditText buy = Ui.field(this, "입고 단가 (1대당 산 값, 원, 선택)", false);
        buy.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        f.addView(model);
        f.addView(serials);
        f.addView(scanButtons(serials, true));
        f.addView(qty);
        f.addView(safe);
        if (canPerf()) f.addView(buy);
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
            long by = canPerf() ? parseWon(buy) : 0;
            try {
                if (ss.isEmpty()) {
                    JSONObject r = store.create("stock");
                    r.put("model", m);
                    r.put("serial", "");
                    r.put("qty", qn);
                    r.put("safe", sf);
                    r.put("status", "재고");
                    r.put("deleted", false);
                    if (by > 0) store.costDoc(r.optInt("id"), true).put("buy", by);
                } else {
                    for (String s : ss) {
                        JSONObject r = store.create("stock");
                        r.put("model", m);
                        r.put("serial", s);
                        r.put("qty", 1);
                        r.put("safe", sf);
                        r.put("status", "재고");
                        r.put("deleted", false);
                        if (by > 0) store.costDoc(r.optInt("id"), true).put("buy", by);
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
        f.addView(scanButtons(serial, false));
        f.addView(safe);
        final EditText buy = Ui.field(this, "입고 단가 (산 값, 원)", false);
        buy.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText sell = Ui.field(this, "출고 단가 (판 값, 원)", false);
        sell.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (canPerf()) {
            JSONObject cd = store.costDoc(r.optInt("id"), false);
            if (cd != null && cd.optLong("buy") > 0) buy.setText(String.valueOf(cd.optLong("buy")));
            if (cd != null && cd.optLong("sell") > 0) sell.setText(String.valueOf(cd.optLong("sell")));
            f.addView(buy);
            f.addView(sell);
        }
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
                        if (canPerf()) {
                            long b2 = parseWon(buy), s2 = parseWon(sell);
                            JSONObject cd = store.costDoc(r.optInt("id"), b2 > 0 || s2 > 0);
                            if (cd != null) {
                                cd.put("buy", b2);
                                cd.put("sell", s2);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    store.save();
                    render();
                }).show();
    }

    private void showDirectEquip(String serialPrefill, final java.util.function.Consumer<List<JSONObject>> cb) {
        LinearLayout f = form();
        final EditText model = Ui.field(this, "모델명", false);
        final EditText serial = Ui.field(this, "시리얼", false);
        serial.setText(serialPrefill);
        f.addView(model);
        f.addView(serial);
        f.addView(scanButtons(serial, false));
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
        final AlertDialog[] dref = new AlertDialog[1];
        Button sc = Ui.button(this, "바코드 스캔 (한 번에 한 대)", Ui.TINT, Ui.DEEP);
        sc.setOnClickListener(v -> scanOnce(code -> {
            JSONObject hit = null;
            for (JSONObject r : store.list(store.arr("stock"))) {
                if (!r.optBoolean("deleted") && !r.optString("serial").isEmpty() && r.optString("serial").trim().equalsIgnoreCase(code.trim())) {
                    hit = r;
                    if ("재고".equals(r.optString("status"))) break;
                }
            }
            if (hit != null && "재고".equals(hit.optString("status"))) {
                JSONObject e = new JSONObject();
                try {
                    e.put("sid", hit.optInt("id"));
                    e.put("model", hit.optString("model"));
                    e.put("serial", hit.optString("serial"));
                } catch (Exception ignored) {
                }
                List<JSONObject> one = new ArrayList<>();
                one.add(e);
                if (dref[0] != null) dref[0].dismiss();
                Ui.toast(this, "장비를 추가했어요: " + hit.optString("model") + " · " + code);
                cb.accept(one);
            } else if (hit != null) {
                Ui.toast(this, "이미 출고된 장비예요: " + code);
            } else {
                if (dref[0] != null) dref[0].dismiss();
                Ui.toast(this, "재고에 없는 번호예요. 모델명을 적어 직접 입력해 주세요.");
                showDirectEquip(code, cb);
            }
        }));
        box.addView(sc, Ui.lp(this, 6));
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
        dref[0] = new AlertDialog.Builder(this).setTitle("장비 선택 (재고)").setView(sv)
                .setNegativeButton("닫기", null)
                .setNeutralButton("직접 입력", (d, w) -> showDirectEquip("", cb))
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
        chatRoom = 0;
        content.addView(title("채팅"));
        Button nb = Ui.button(this, "+  새 채팅방 만들기", Ui.GOLD, Ui.DARK);
        nb.setOnClickListener(v -> showAddRoom());
        content.addView(nb, Ui.lp(this, 8));
        content.addView(Ui.text(this, "직원  (눌러서 1:1 대화)", 13, Ui.GRAY, true), Ui.lp(this, 10));
        HorizontalScrollView sh = new HorizontalScrollView(this);
        sh.setHorizontalScrollBarEnabled(false);
        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        List<JSONObject> staff = new ArrayList<>();
        JSONArray sua = store.users();
        for (int i = 0; i < sua.length(); i++) {
            JSONObject u = sua.optJSONObject(i);
            if (u.optInt("id") != myId() && u.optBoolean("active", true)) staff.add(u);
        }
        Collections.sort(staff, (x, y) -> x.optString("name").compareTo(y.optString("name")));
        for (final JSONObject u : staff) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
            col.addView(avatarView(u.optInt("id"), 52));
            TextView nmv = Ui.text(this, u.optString("name"), 12, Ui.INK, false);
            nmv.setSingleLine(true);
            nmv.setGravity(Gravity.CENTER);
            col.addView(nmv, new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
            col.setOnClickListener(v -> startDm(u.optInt("id")));
            srow.addView(col);
        }
        if (staff.isEmpty()) srow.addView(Ui.text(this, "아직 다른 직원이 없어요.", 13, Ui.GRAY, false));
        sh.addView(srow);
        content.addView(sh, Ui.lp(this, 4));
        content.addView(Ui.text(this, "대화방", 13, Ui.GRAY, true), Ui.lp(this, 12));
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
            String ttl = roomTitle(r);
            row.addView(Ui.tile(this, ttl.isEmpty() ? "?" : ttl.substring(0, 1), Ui.TINT, Ui.DEEP));
            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(Ui.dp(this, 14), 0, 0, 0);
            mid.addView(Ui.text(this, ttl, 16, Ui.INK, true));
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
                showRoom();
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
                    showRoom();
                }).show();
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
            {"채팅 탭", "직원끼리 대화하는 곳이에요. 원하는 만큼 채팅방을 만들 수 있습니다.\n\n■ 채팅방 만들기\n- 맨 위 '+ 새 채팅방 만들기' → 이름을 적고 '만들기'. (예: 거래처 A/S, 서류 접수, 입금 확인)\n- 방이 하나도 없으면 '기본 채팅방 만들기'로 전체방·서류접수·입금확인·신규계약·AS설치·공지 방을 한 번에 만들 수 있어요.\n\n■ 대화방\n- 방을 누르면 전체 화면 대화방이 열리고, 입력줄은 맨 아래에 고정됩니다. 왼쪽 위 ‹ 또는 폰의 뒤로가기로 목록에 돌아갑니다.\n- 내 말풍선은 금색·오른쪽, 다른 사람은 흰색·왼쪽이고 옆에 이름과 프로필 사진이 나옵니다. 같은 사람이 같은 분에 이어 보내면 사진·이름은 한 번만 나와요.\n- 사람 사진을 누르면 그 직원의 프로필(이름·상태 메시지·전화번호)이 나오고, '전화하기'로 바로 걸 수 있어요.\n\n■ 답장 · 반응 · 삭제 (v0.8.0, 텔레그램처럼)\n- 말풍선을 두 번 빠르게 누르면 ❤️ 좋아요가 붙습니다. 같은 걸 다시 두 번 누르면 취소돼요.\n- 말풍선을 길게 누르면 맨 위에 이모티콘 줄(👍 ❤️ 😂 😮 😢 🙏 🔥 👏)이 뜨고, 그 아래에 답장 / 복사(글만) / 삭제(내 메시지만)가 있어요. 이모티콘을 한 번 누르면 바로 붙습니다.\n- 답장을 고르면 입력줄 위에 '○○에게 답장' 띠가 생기고, 그 상태로 보내면 말풍선 위에 인용이 붙어요. 띠의 ✕ 로 취소합니다.\n- 이미 붙은 이모티콘을 누르면 같은 목록이 떠서 다른 이모티콘으로 바꾸거나, 내 이모티콘을 한 번 더 눌러 지울 수 있어요(텔레그램처럼).\n- 삭제한 내 메시지는 대화에서 사라지고, 서버에는 기록이 보관됩니다.\n\n■ 링크 (v0.9.1)\n- 글에 웹 주소(예: https://… 또는 naver.com)를 적어 보내면 파란 밑줄로 표시되고, 말풍선을 한 번 누르면 브라우저로 열립니다. 주소가 여러 개면 목록에서 고르세요. 길게 누르기·두 번 누르기는 그대로 됩니다.\n\n■ 사진·파일·이모지·스티커\n- 입력줄 왼쪽 '+' → 사진 보내기(앨범) / 사진 찍어 보내기 / 파일 보내기.\n- 사진은 자동으로 줄여서 올라가요. 대화 속 사진을 누르면 크게 보이고, '다른 앱으로 열기·저장'으로 저장할 수 있습니다.\n- 파일은 3MB까지 보낼 수 있어요(문서·엑셀·PDF용). 더 큰 파일은 지금은 보낼 수 없습니다. 파일 말풍선을 누르면 내려받아 열립니다.\n- 입력줄 오른쪽 ☺ 를 누르면 판이 열려요. 맨 위 탭: 이모지(글 사이에 들어감) / 나인이 / 복이 / 호야 / 깍이 / 도리. 스티커는 누르면 바로 전송됩니다.\n- '나인이 프렌즈'는 우리 회사 캐릭터 5마리예요(아홉꼬리 여우 나인이, 복두꺼비 복이, 아기 호랑이 호야, 까치 깍이, 다람쥐 도리). 마리당 16종, 모두 80종으로 신규계약 성공·축하·대단해·빡쳐·정신 차려·힘내와 우리 일(POS 설치, 현장 AS, 랜선 공사, 단말기 수리·도색, 토스 영업, 테이블오더, 회의, 서류, 보고서, 차에서 꿀잠 등)이 들어 있어요.\n- 예전(v0.8.0)에 보낸 나인이 스티커 메시지는 그대로 보입니다.\n\n■ 보내는 중 안내\n- 사진·파일은 인터넷이 연결돼야 올라가요. '보내는 중…'이 뜬 뒤 대화에 나타납니다. 안 나타나면 인터넷을 확인하고 다시 보내 주세요.\n\n■ 1:1 비공개 대화 (v0.12.0)\n- 채팅 탭 맨 위 직원 목록에서 직원을 누르거나, 채팅에서 직원 사진을 눌러 '1:1 대화'를 누르면 둘만의 방이 열려요. 같은 사람과는 항상 같은 방이 열립니다.\n- 이 방은 두 사람 사이의 대화라서 다른 직원에게는 내려가지 않아요(최고관리자 포함). 목록에는 상대 이름으로 보여요.\n- 답장·반응·스티커·사진·파일은 일반 방과 같이 쓸 수 있어요. (사진·파일 자체는 보관 주소를 아는 사람만 열 수 있어 사실상 두 사람만 열어요.)\n- 이 방은 이름을 바꿀 수 없고, 업데이트(v0.12.0) 전의 앱에는 보이지 않아요.\n\n※ 1:1 대화를 뺀 나머지 채팅방은 모든 직원이 볼 수 있습니다. 민감한 개인 이야기·개인정보 파일은 1:1 대화로 나누세요."},
            {"일정 탭 (달력)", "한국 달력과 앞으로의 일정을 함께 봅니다.\n\n■ 달력\n- 일요일·공휴일은 빨간색, 토요일은 파란색, 오늘은 금색 원입니다. 공휴일 이름도 표시돼요. (2026~2028년 기준)\n- ‹ › 로 달을 넘기고 '오늘'을 누르면 오늘로 돌아옵니다.\n- 일정이나 업무 기한이 있는 날은 작은 금색 점이 찍혀요.\n- 날짜를 누르면 그날의 일정과 업무가 아래에 나옵니다.\n\n■ 일정 남기기\n1. 오른쪽 아래 + 버튼.\n2. 제목과 날짜를 적고, 시간은 선택입니다.\n3. '가맹점 조회·선택'을 누르면 등록된 가맹점 중에서 찾아 고릅니다 (초성 검색 가능). 고르면 사업자번호·연락처·주소·계약조건·등급이 불러와지고, 장소에는 가맹점 주소가 자동으로 들어갑니다.\n4. '추가'를 누르면 달력에 바로 표시되고, 달력 아래 '다가오는 일정'에도 올라갑니다.\n\n■ 다가오는 일정\n- 오늘부터 앞으로의 일정과 내 업무 기한이 날짜 순으로 나와서, 언제 어디로 가야 하는지 한눈에 확인할 수 있습니다.\n- 가맹점이 연결된 일정을 누르면 가맹점 상세 정보가 열립니다."},
            {"전체 탭", "나머지 모든 기능으로 들어가는 메뉴판입니다.\n\n■ 기능 검색 (v0.8.0)\n- 맨 위 프로필 카드 아래 '🔍 기능 검색' 칸에 단어나 초성을 치면 그 기능이 바로 나옵니다. 예: '재고', 'ㅈㄱ', '문자', '수익', '바코드', '프로필'.\n- 눌러서 바로 이동해요. 지우면 원래 메뉴판으로 돌아갑니다. (업무·채팅·일정 같은 탭도 검색됩니다.)\n\n■ 메뉴\n- 맨 위 내 카드를 누르면 프로필 사진과 상태 메시지를 바꿀 수 있어요.\n- 재고: 장비 재고 관리 (바코드 스캔, 단가)\n- 가맹점: 가맹점 정보 관리\n- 신규 계약 올리기 / 계약 관리: 계약 등록과 설치 완료 처리, 만료 알림\n- 단체 문자 보내기: 가맹점에 안내 문자\n- 내 실적: 나만 보는 월별 실적과 지난달 비교\n- 전체 실적·직원별 순위 / 수익 현황 / 실적 보고서 올리기: (권한이 있는 사람만 보임)\n- 일일보고 쓰기, 일일보고 종합 보기(권한자)\n- 지난 업무 기록: 완료된 업무 검색\n- 사용설명서, 버전·업데이트 내역, 공지사항\n- 직원과 권한: (관리자만) 직원 추가·권한 설정\n- 로그아웃\n\n※ 맨 아래에 현재 앱 버전이 표시됩니다. 문의할 때 이 버전을 알려 주세요."},
            {"재고", "장비를 우리가 직접 입력해서 관리합니다. (전체 → 재고)\n\n■ 재고 추가\n1. 오른쪽 아래 + 버튼.\n2. 모델명을 적고, 시리얼은 한 줄에 하나씩 적습니다. 여러 줄을 붙여 넣으면 장비가 한꺼번에 등록돼요.\n3. 시리얼이 없는 소모품(롤지 등)은 시리얼 대신 '수량'만 적습니다.\n4. 안전재고: 이 수 아래로 내려가면 빨간색으로 '부족해요!'가 표시됩니다.\n5. 입고 단가: 실적 권한자에게만 보이는 칸입니다. 1대당 산 값을 적으면 시리얼 전체에 같이 적용돼요.\n\n■ 바코드 스캔\n- '바코드 스캔'을 누르면 카메라가 열리고, 읽으면 번호가 시리얼 칸에 한 줄 들어갑니다. 한 번 누르면 한 번만 읽어요. 다음 장비는 버튼을 다시 눌러 주세요. 같은 번호는 두 번 들어가지 않아요.\n- 재고 목록 위 '바코드로 찾기'를 누르면 바코드를 읽어 그 시리얼을 바로 검색합니다.\n- 계약에서 '+ 장비 추가'를 누르고 '바코드 스캔(한 번에 한 대)'을 누르면, 읽은 번호가 재고에 있을 때 그 장비가 바로 추가돼요. 다음 장비는 다시 '+ 장비 추가'를 눌러 같은 방식으로 넣습니다. 재고에 없는 번호면 모델명만 적어 직접 입력하게 열려요.\n\n■ 시리얼 촬영 인식 (v0.7.0)\n- '시리얼 촬영'을 누르고 장비 뒷면의 S/N 라벨을 가까이서 찍으면 글자를 읽어 줍니다.\n- 시리얼처럼 보이는 글자 후보가 목록으로 나오고, 맞는 것을 체크해서 '넣기'를 누르세요. 0(영)과 O(오), 1과 I처럼 헷갈리기 쉬운 글자는 꼭 눈으로 확인해 주세요.\n- 바코드가 같이 있으면 바코드 스캔이 더 정확합니다.\n- 스캔과 촬영은 구글 서비스가 있는 폰에서 동작합니다.\n\n■ 보기·검색\n- 모델별로 묶여서 '재고 n대'가 보이고, 그 아래에 시리얼이 나열됩니다.\n- 검색창에 모델명·시리얼을 치면 찾아줘요. 'ㅌㅅㅍㅅ' 같은 초성도 됩니다.\n\n■ 수정·삭제\n- 시리얼 줄을 누르면 수정창이 열립니다. 시리얼 칸에도 스캔·촬영 버튼이 있어요. '삭제'를 누르면 목록에서 지워져요 (기록은 서버에 남아 복구 가능).\n\n■ 단가와 수익 (실적 권한자 전용)\n- 재고 줄을 누르면 입고 단가(산 값)와 출고 단가(판 값)를 적을 수 있어요.\n- 재고 화면 맨 위에 보유 재고 자산(입고 단가 합계)이 나오고, '수익 현황 보기'로 이동합니다.\n- 단가는 서버에서도 실적 권한자만 읽을 수 있게 따로 보관됩니다. 권한이 없는 직원에게는 칸 자체가 보이지 않아요.\n\n■ 상태가 바뀌는 순서\n재고 → (계약에서 선택하면) 출고중 → (설치 완료 처리하면) 재고 목록에서 사라짐"},
            {"가맹점", "우리 거래처(가맹점) 정보를 관리합니다. (전체 → 가맹점)\n\n■ 등록\n- 오른쪽 아래 + 버튼 → 상호(필수), 사업자번호, 연락처, 주소, 계약조건, 등급 코드, 메모.\n- 계약·일정·업무 지시 화면의 '가맹점 조회'에서 바로 '새 가맹점 등록'도 가능합니다.\n\n■ 등급 코드\n밴피(VAN) 기준입니다.\n0 S · 월 5만원 이상\n1 A · 3만원 이상\n2 B · 1만원 이상\n3 C · 1만원 미만\n4 해지가맹점\n5 신규가맹점\n7 협력사\n\n■ 수정\n- 목록에서 가맹점을 누르면 정보가 보이고, '수정'으로 바꿀 수 있습니다.\n\n■ 삭제 (최고관리자만, v0.8.0)\n- 가맹점 정보 창의 '삭제', 또는 수정 창의 '삭제'를 누르면 확인 창이 나오고 목록에서 지워집니다.\n- 그 가맹점에 연결된 계약·일정이 몇 건인지 같이 알려 드려요. 이미 올라간 계약·일정 기록은 그대로 남고, 서버에도 기록이 보관됩니다.\n- 관리자가 아닌 직원에게는 삭제 버튼이 보이지 않아요.\n\n■ 검색\n- 상호·사업자번호·전화·주소 어느 것으로든, 초성으로도 검색됩니다."},
            {"신규 계약 올리기", "계약이 되면 바로 올려 주세요. (전체 → 신규 계약 올리기, 또는 계약 관리 화면의 + 버튼)\n\n1. 가맹점 조회·선택: 등록된 가맹점을 고릅니다. 없으면 '새 가맹점 등록'.\n2. 결제 연동: VAN사 또는 PG사, 업체명 (예: 다우데이타).\n3. 구분을 고르면 금액 입력 이름이 바뀝니다.\n   - 판매 → 판매금액\n   - CMS → CMS 금액\n   - 렌탈 → 월 렌탈료\n4. 나가는 장비: '+ 장비 추가'를 눌러 재고에서 체크해 고릅니다. 재고에 없으면 '직접 입력'으로 모델명·시리얼을 적어요. 잘못 고른 장비는 오른쪽 − 로 뺍니다. 장비를 여러 번 + 해서 여러 대를 넣을 수 있어요.\n5. 장비 메모: 설치 조건 등 자유롭게 적습니다.\n6. 계약일과 만료일: 만료일 아래 '+12개월 / +24개월 / +36개월' 버튼을 누르면 계약일 기준으로 자동 계산됩니다.\n7. '올리기'.\n\n※ 올리면 고른 장비는 재고에서 '출고중'이 됩니다."},
            {"계약 관리 · 설치 완료 · 만료 알림", "(전체 → 계약 관리)\n\n■ 계약 보기\n- 위쪽에 '만료가 가까운 계약(3개월 이내)'이 빨간 D-숫자와 함께 먼저 나옵니다. 아래는 전체 계약입니다.\n- 일반 직원은 내가 올린 계약만, 실적 권한이 있는 사람은 전체 계약이 보입니다.\n\n■ 설치 완료\n- 계약을 누르고 '설치 완료'를 누르면 나간 장비가 재고에서 완전히 빠집니다.\n\n■ 장비 추가\n- 같은 가맹점에 장비가 더 나가면 계약을 열어 '장비 추가'를 눌러 모델명·시리얼을 이어서 추가합니다.\n\n■ 만료 알림\n- 만료가 90일 이하로 남은 계약이 있으면 '업무' 탭 위에 알림 카드가 뜨고, 전체 탭에도 숫자가 표시됩니다."},
            {"실적 (권한자 전용)", "■ 내 실적 (전체 → 내 실적)\n- 나만 볼 수 있습니다. 이번 달 신규 계약 대수, 판매금액, 올린 계약 목록, 지난달과의 비교가 나와요.\n\n■ 전체 실적·직원별 순위 (실적 보기 권한자)\n- 위 ‹ › 로 월을 바꿉니다.\n- 신규 계약 대수와 판매금액, 직원별 순위 막대 그래프, 올라온 계약 목록이 나옵니다.\n- 월별 성과 (v0.7.0): '지난달 → 선택한 달' 계약 대수와 성장률(▲ 늘어남 / ▼ 줄어듦, 지난달이 0이면 '신규')을 직원별 막대 두 개(회색 지난달, 금색 이번 달)로 보여 줍니다. 그 아래 최근 6개월 추이 그래프도 있어요.\n- 계약을 누르면 상세 정보와 장비를 볼 수 있어요.\n\n■ 수익 현황 (실적 보기 권한자, 전체 → 수익 현황)\n- 지금까지 총 수익, 선택한 달 수익(매출·매입), 최근 6개월 수익 그래프, 직원별 수익이 나옵니다.\n- 계약에 올라가 출고된 장비(출고중·설치완료)의 '출고 단가 − 입고 단가'를 합산하고, 월은 계약일 기준이에요.\n- 단가를 아직 안 적은 장비는 수익에서 빠지고, 맨 아래 목록에 나옵니다. 눌러서 단가를 적으면 바로 반영돼요."},
            {"가맹점 일괄 가져오기 (엑셀·CSV)", "(v0.11.0, 전체 → 가맹점 → 맨 위 '가맹점 일괄 가져오기')\n\n■ 준비\n- 이카운트(또는 다른 곳)에서 거래처 목록을 엑셀로 내려받아요. 형식은 .xlsx 또는 CSV 입니다. 옛 .xls 는 엑셀에서 .xlsx 로 다시 저장해 주세요.\n- 첫 줄(머리글)에 '거래처명'(또는 상호·가맹점명)이 있어야 합니다. 사업자등록번호, 거래처코드, 대표자, 전화, 핸드폰, 주소, 등급, 업태, 종목, 메모 열이 있으면 함께 가져와요.\n\n■ 가져오기\n1. 버튼을 눌러 파일을 고릅니다.\n2. 미리보기에서 새로 등록·갱신·건너뜀 개수와 읽은 열을 확인합니다.\n3. '가져오기'를 누르면 등록됩니다.\n\n■ 다시 올려도 안전해요\n- 같은 거래처코드, 사업자번호, 상호가 있으면 새로 만들지 않고 갱신합니다. 파일에 값이 있는 칸만 덮어쓰고 빈 칸은 그대로예요.\n- 새 가맹점의 등급은 파일에 등급이 없으면 '5코드 · 신규가맹점'으로 들어갑니다.\n- 핸드폰 열이 있으면 단체 문자에 그 번호를 씁니다."},
            {"입금 확인 · 비상연락망 · 알림 · 백업", "(v0.10.0)\n\n■ 입금 확인 (전체 → 입금 확인)\n- 오른쪽 아래 + → 종류(판매대금/미납금/정보변경수수료/기타), 금액, 가맹점 선택, 메모를 넣고 '올리기'.\n- 올리면 '입금확인' 채팅방에 자동으로 글이 올라가요(방이 없으면 만들어요). 다른 직원 폰에는 알림이 갑니다.\n- 내역을 눌러 '확인 처리'를 하면 확인한 사람 이름이 남고 채팅방에도 알려요.\n\n■ 비상연락망 (전체 → 비상연락망)\n- + 로 이름·구분(본사, 카드사, 밴사, 직원 등)·번호를 등록해요. 연락처를 누르면 전화 화면이 열립니다. 길게 누르면 수정·삭제.\n\n■ 서류 업무 자동 알림\n- 업무 지시에서 종류가 '서류 접수' 또는 '카드사 서류'이면 '서류접수' 채팅방에 담당자·거래처·기한이 자동으로 올라갑니다.\n\n■ 폰 알림\n- 내게 새 업무가 지시되거나, 새 채팅·공지·입금 요청이 오면 폰 알림이 떠요. 처음 한 번 알림 허용을 눌러 주세요. 앱이 완전히 종료된 뒤에는 알림이 오지 않을 수 있습니다(완전한 푸시는 서버 요금제 전환 후 지원).\n\n■ 데이터 백업 (관리자)\n- 전체 → 데이터 백업 내려받기: 업무·가맹점·계약·재고·채팅 글 등 전체 자료를 파일로 만들어 드라이브 등에 보관할 수 있어요. 정기적으로(예: 매주) 받아 두세요."},
            {"자료실 (v0.13.0)", "양식·계약서·안내문 같은 파일을 올려 두고 직원 모두가 내려받아 쓰는 게시판이에요.\n\n■ 찾아가기\n- 전체 탭 → 회사 → 자료실\n\n■ 자료 올리기\n- 오른쪽 아래 + 를 누르고 제목(필수)·설명·분류를 적어요.\n- '📎 파일 추가'로 파일을 고르면 바로 올라가고, 한 글에 5개까지 붙일 수 있어요. 사진은 자동으로 줄여서 올라가고, 문서·엑셀·PDF 같은 파일은 3MB까지입니다.\n- 파일이 다 올라간 뒤(올리는 중 표시가 사라지면) '올리기'를 누르세요.\n- 분류: 양식·서식 / 계약·약정 / 가맹점 안내 / 교육·매뉴얼 / 사진·홍보물 / 기타\n\n■ 찾기·내려받기\n- 위쪽 분류 버튼으로 걸러 보고, 검색창에 제목·설명·파일 이름(초성도 가능)을 적어 찾아요.\n- 글을 누르면 설명과 첨부 파일이 나와요. 파일을 누르면 내려받아 열리고, 열린 앱에서 저장·공유할 수 있어요.\n\n■ 고치기·지우기\n- 올린 사람과 최고관리자만 수정·삭제할 수 있어요. 삭제해도 서버에는 기록이 남습니다.\n- 새 자료가 올라오면 다른 직원 폰에 알림이 떠요(앱이 켜져 있을 때).\n\n※ 개인정보가 든 파일(신분증 사본 등)은 올리지 마세요. 모든 직원이 볼 수 있습니다."},
            {"가맹점 보호·복원 (최고관리자)", "가맹점 정보가 사라지지 않도록 여러 겹으로 지켜요.\n\n■ 지켜 주는 장치\n1) 서버에서는 어떤 자료도 지울 수 없게 막아 두었어요.\n2) 가맹점 삭제는 목록에서 '숨김' 처리일 뿐이라 언제든 복원할 수 있어요.\n3) 가맹점을 열면 '🕘 변경 이력 · 되돌리기'가 있어요. 누가 언제 무엇을 바꿨는지 보고, 눌러서 그 시점으로 되돌립니다.\n4) 엑셀 가져오기 직전, 복원 직전, 이력 되돌리기 직전에 현재 상태를 자동 저장해요('되돌리기 기준점'). 저장에 실패하면 작업을 시작하지 않습니다.\n5) 최고관리자 폰은 앱을 열면 하루 한 번 전체 자료를 14일치까지 자동 백업해요.\n6) 전체 → '데이터 백업 내려받기'로 만든 파일은 드라이브·카톡 등 폰 밖에 따로 보관하세요.\n\n■ 복원하는 방법\n- 가맹점 화면 위쪽 '🛡 가맹점 보호·복원' (또는 전체 → 관리자 → 가맹점 보호·복원)\n- '삭제한 가맹점 복원': 숨긴 가맹점 목록에서 눌러 복원\n- '백업·되돌리기 목록에서 복원': 자동백업이나 되돌리기 기준점을 고르면 지금과 비교해 미리 보여 주고, 확인하면 복원해요. 가져오기를 취소하고 싶으면 '가져오기전' 기준점을 고르세요(그 뒤 새로 생긴 가맹점은 숨김 처리됩니다).\n- '백업 파일을 직접 골라 복원': 내려받아 둔 백업(.json)을 골라요.\n- 복원할 때도 직전 상태가 자동 저장되니, 잘못 복원해도 다시 돌릴 수 있어요.\n\n※ 폰 안 백업은 이 폰을 바꾸거나 앱을 지우면 함께 사라져요. 중요한 시점(큰 가져오기 전)에는 '데이터 백업 내려받기'로 파일을 따로 받아 두세요. 카드(Blaze) 등록 후에는 서버 자동 백업도 켜겠습니다."},
            {"공지사항 · 직원과 권한", "■ 공지사항 (전체 → 공지사항)\n- 공지 쓰기 권한이 있으면 + 버튼으로 작성합니다. '맨 위에 고정'을 켜면 항상 위에 보여요.\n- 읽지 않은 공지는 ● 표시와 전체 탭 숫자로 알려줍니다. 누르면 읽음 처리돼요.\n\n■ 직원과 권한 (관리자 전용)\n- + 버튼 → 이름, 휴대폰 번호, 권한 묶음을 선택해 직원을 등록합니다. 등록된 번호로만 로그인할 수 있어요.\n- 직원을 누르면 항목별 권한을 켜고 끌 수 있고, '사용 중지'로 퇴사자의 접근을 막습니다.\n- 마지막 관리자는 중지하거나 권한을 끌 수 없습니다.\n\n※ '실적 보기'(수익 현황과 재고 단가 포함)와 '일일보고·실적보고 종합 보기'는 서버에서 강제로 막혀 안전합니다. 나머지 권한은 화면에서 숨기는 방식이에요."},
            {"일일보고 · 실적 보고서", "■ 일일보고 쓰기 (전체 → 일일보고 쓰기)\n- 날짜를 확인하고, 오늘 한 일·특이사항·내일 계획을 적어 '제출하기'를 누릅니다.\n- 그날 완료한 업무와 올린 계약은 자동으로 같이 집계돼요.\n- 같은 날 다시 쓰면 수정본으로 덮어씁니다. 아래에서 내가 낸 보고를 다시 열어 볼 수 있어요.\n\n■ 일일보고 종합 (대표님)\n- 전체 → 일일보고 종합 보기: 날짜별로 직원별 보고가 한 화면에 모이고, 미제출자는 빨간색으로 표시됩니다.\n- 이 메뉴는 '일일보고·실적보고 종합 보기' 권한이 켜진 사람만 봅니다. 관리자도 예외가 아니에요(v0.7.0부터). 처음에는 관리자에게 켜져 있고, 대표님을 등록하면 대표님께 켜 드린 뒤 관리자 쪽을 끄면 됩니다.\n\n■ 실적 보고서 (본부장 → 대표님)\n- 전체 → 실적 보고서 올리기: 이번 달 실적이 자동 집계되어 채워져 있고, 고쳐서 '대표님께 보고하기'를 누릅니다.\n- 대표님은 같은 메뉴에서 올라온 보고서를 봅니다.\n\n※ 보고서 양식은 정해지면 그 모양에 맞춰 바꿀 수 있어요."},
            {"단체 문자 (가맹점)", "(전체 → 단체 문자 보내기, 또는 전체 탭 검색창에 '문자')\n\n■ 이렇게 동작해요\n- 내용을 쓰고, 보낼 가맹점을 고른 뒤 '문자 보내기 시작'을 누르면 가맹점 한 곳씩 이 폰의 문자 앱이 열립니다. 내용이 미리 채워져 있으니 확인하고 '전송'만 누르면 돼요.\n- 한 곳씩 따로 보내서 받는 분들끼리 번호가 보이지 않습니다. 문자 요금은 이 폰의 요금제로 나갑니다.\n- 내용에 {상호} 라고 쓰면 가맹점 이름으로 바뀝니다. 예: {상호} 사장님, 안녕하세요. 나인페이먼츠입니다.\n\n■ 받는 가맹점 고르기\n- 칸을 눌러 하나씩 고르거나, 위의 'S 등급 선택' 같은 버튼으로 등급 전체를 한 번에 고릅니다. '보이는 전체 선택'은 검색 결과 전체를 고르고, '선택 해제'는 모두 풉니다.\n- 유선 번호(02·062 등)는 문자를 받을 수 없어서 선택할 수 없어요.\n\n■ 보내는 순서\n1. '문자 앱 열기' → 문자 앱에서 전송.\n2. 이 앱으로 돌아오면 다음 가맹점이 준비돼 있습니다. 필요 없으면 '건너뛰기', 그만두려면 '중단'.\n3. 끝나면 문자 앱의 보낸 기록에서 보내졌는지 확인하세요. 앱은 전송을 눌렀는지 알 수 없어요.\n\n※ 수십 곳 이상을 한꺼번에 보내는 기능(문자 업체 연동)은 서버 요금제 전환 후에 만들 예정입니다."},
            {"프로필 · 버전", "■ 프로필 (v0.7.0)\n- 전체 탭 맨 위 내 카드(또는 오른쪽 위 동그라미)를 누르면 프로필이 열립니다.\n- '사진 변경'으로 앨범에서 사진을 고르면 자동으로 동그랗게 잘려 저장되고, 채팅과 화면 위쪽에 나와요.\n- '상태 메시지'에 외근 중, 오후 복귀 같은 한 줄을 적어 두면 다른 직원이 프로필에서 볼 수 있어요.\n- 채팅에서 다른 직원 사진을 누르면 그 직원의 프로필과 전화하기 버튼이 나옵니다.\n\n■ 버전 보기\n- 전체 탭 맨 아래, 로그인 화면, 전체 → 버전·업데이트 내역에 현재 버전이 표시됩니다. (예: v0.7.0 (빌드 9))\n- 업데이트 내역에는 버전마다 무엇이 바뀌었는지 적혀 있어요.\n\n■ 버전 번호 규칙\n- 가운데 숫자(0.7.0): 새 기능이나 새 메뉴가 들어갈 때 올라갑니다.\n- 끝 숫자(0.7.1): 새 기능 없이 오류를 고치거나 문구·디자인을 다듬을 때 올라갑니다.\n- 괄호 안 빌드 번호: 배포할 때마다 1씩 올라갑니다.\n- 실제 문자 인증으로 전 직원이 쓰기 시작하면 1.0.0이 됩니다.\n- 새 버전이 나올 때마다 이 사용설명서도 함께 새로 고쳐집니다."},
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



    // ---------- 버전 표시 · 업데이트 내역 (규칙은 VERSIONING.md) ----------
    private String appVersion() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "v" + pi.versionName + " (빌드 " + pi.getLongVersionCode() + ")";
        } catch (Exception e) {
            return "v?";
        }
    }

    /** {버전, 날짜, 내용}. 새 버전은 맨 위에 추가. CI 가 현재 versionName 항목이 있는지 검사한다. */
    private static final String[][] CHANGELOG = {
            {"0.13.0", "2026-10-24", "• 자료실 게시판: 양식·계약서·안내문 등 파일을 올리고 내려받기 (분류·검색·수정·삭제, 한 글에 파일 5개, 사진은 자동으로 줄임)\n• 채팅에서 자물쇠 표시와 '둘만 보여요' 문구를 없앰 (1:1 대화는 그대로 두 사람 사이에서만 오가요)"},
            {"0.12.1", "2026-10-24", "• 채팅 탭 맨 위에 직원 목록(사진·이름)이 생겨서, 직원을 누르면 바로 1:1 대화가 열려요 (이미 있으면 그 방으로)\n• 1:1 대화도 단체방과 같은 기능(답장·반응·스티커·사진·파일·링크·삭제)을 그대로 씁니다"},
            {"0.12.0", "2026-10-24", "• 가맹점 정보 보호 장치 6겹: ① 서버에서 지우기 금지 ② 삭제는 '숨김'이라 휴지통에서 복원 ③ 가맹점마다 변경 이력을 보고 그 시점으로 되돌리기 ④ 엑셀 가져오기·복원 직전에 자동 저장 ⑤ 관리자 폰이 하루 한 번 14일치 자동 백업 ⑥ 백업 파일(내려받은 것 포함)로 가맹점 복원\n• 가맹점 → 🛡 가맹점 보호·복원 메뉴 신설 (최고관리자)\n• 1:1 비공개 대화: 채팅 탭 직원 목록, 직원 프로필의 '1:1 대화' 버튼. 서버 규칙으로 두 사람에게만 보이고 관리자도 볼 수 없어요\n• 엑셀 가져오기 직전 상태를 자동 저장하고, 저장에 실패하면 가져오기를 시작하지 않음"},
            {"0.11.0", "2026-10-24", "• 가맹점 일괄 가져오기: 이카운트 등에서 내려받은 엑셀(.xlsx)·CSV 를 올리면 머리글을 알아보고 미리보기 후 등록·갱신 (같은 거래처코드·사업자번호·상호는 중복 없이 갱신)\n• 휴대폰 번호 열이 따로 있으면 단체 문자에 사용"},
            {"0.10.1", "2026-10-24", "• 나인이 프렌즈 스티커 80종을 다시 그림: 점프·기울기·발 구르기·눈물·연기·소품 등 문구에 맞는 동작으로 다양하게"},
            {"0.10.0", "2026-10-24", "• 입금 확인: 입금 내역(판매대금·미납금·정보변경수수료·기타)을 올리고 가맹점을 표시, '입금확인' 채팅방에 자동 알림, 확인 처리\n• 비상연락망: 이름·구분·번호 목록, 누르면 바로 전화, 검색·수정·삭제\n• 서류 업무를 지시하면 '서류접수' 채팅방에 자동으로 알림 글\n• 새 업무·채팅·공지·입금이 올라오면 폰 알림\n• 관리자: 데이터 백업 내려받기(JSON 파일)"},
            {"0.9.1", "2026-10-24", "• 채팅 말풍선 속 링크(주소)를 누르면 바로 열려요 (여러 개면 고르기). 링크는 파란 밑줄로 보입니다"},
            {"0.9.0", "2026-10-24", "• 새 스티커 '나인이 프렌즈' 5마리 80종: 나인이(아홉꼬리 여우)·복이(두꺼비)·호야(아기 호랑이)·깍이(까치)·도리(다람쥐). 신규계약 성공·축하·빡쳐·힘내 + 우리 업무(POS 설치, AS, 랜선, 단말기 수리·도색, 토스 영업, 차에서 꿀잠…)\n• 채팅 반응을 텔레그램처럼: 두 번 탭하면 ❤️, 붙은 이모티콘을 누르면 하트·엄지 등 목록이 떠서 바로 고르기, 길게 누르면 맨 위에 이모티콘 줄"},
            {"0.8.0", "2026-10-24", "• 채팅: 말풍선 두 번 누르면 ❤️ 좋아요, 길게 누르면 답장·이모티콘 반응·복사·삭제 (텔레그램 방식)\n• 우리 회사 캐릭터 '나인이' 스티커 12종, 이모지/스티커 판\n• 채팅 파일을 3MB까지 보낼 수 있게 확대\n• 가맹점 삭제 (최고관리자만, 수정 창에도 삭제 버튼)\n• 바코드는 한 번 누르면 한 번만 입력, 계약 장비 추가에서 바코드를 읽으면 그 장비가 바로 추가\n• 전체 탭 맨 위 기능 검색(단어·초성)\n• 가맹점 단체 문자: 폰 문자 앱으로 한 곳씩, {상호} 자동 치환"},
            {"0.7.0", "2026-10-24", "• 채팅 개편: 입력줄 맨 아래 고정, 내 말풍선·상대 말풍선 구분, 시간 표시, 날짜 띠\n• 채팅에 사진·파일(700KB까지)·이모티콘 보내기\n• 프로필 사진과 상태 메시지, 채팅에서 사진을 누르면 프로필·전화하기\n• 재고 바코드 스캔(연속 스캔, 바코드로 찾기)과 시리얼 라벨 촬영 인식\n• 재고 입고·출고 단가(권한자 전용), 보유 재고 자산, 수익 현황(누적·월별·직원별)\n• 실적: 지난달 대비 성장률과 최근 6개월 그래프\n• 일일보고 종합 보기를 관리자와 분리해 권한 항목 하나로만 켜고 끔\n• 앱 버전 표시와 업데이트 내역, 가맹점 등급 코드를 밴피 기준으로 변경"},
            {"0.6.0", "2026-10-24", "• 일정: 달력에서 일정을 눌러 상세 보기·삭제, 회사 일정은 최고관리자만 추가\n• 내 실적(본인만) / 전체 실적·직원별 순위(권한자)\n• 일일보고 쓰기, 대표님용 일일보고 종합, 실적 보고서"},
            {"0.5.0", "2026-10-24", "• 5개 탭(업무/프로젝트/채팅/일정/전체), 채팅방, 재고, 가맹점, 계약(장비·만료 알림), 사용설명서"},
            {"0.4.0", "2026-10-23", "• 휴대폰 문자 인증 로그인, 서버 실시간 저장"},
    };

    private void renderAbout() {
        content.addView(title("버전·업데이트 내역"));
        content.addView(Ui.text(this, "현재 버전  " + appVersion(), 15, Ui.INK, true), Ui.lp(this, 4));
        content.addView(Ui.text(this, "버전 규칙: 새 기능이 들어가면 가운데 숫자(0.7.0), 오류 수정·문구 변경이면 끝 숫자(0.7.1)가 올라갑니다. 괄호 안 빌드 번호는 배포할 때마다 1씩 올라가요.", 12, Ui.GRAY, false), Ui.lp(this, 6));
        for (String[] c : CHANGELOG) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, "v" + c[0] + "   " + c[1], 15, Ui.DEEP, true));
            TextView t = Ui.text(this, c[2], 14, Ui.INK, false);
            t.setLineSpacing(0, 1.3f);
            card.addView(t, Ui.lp(this, 6));
            content.addView(card, Ui.lp(this, 10));
        }
    }


    // ======================= v0.7: 채팅 개편 · 프로필 · 사진/파일 =======================
    private boolean inRoom = false;
    private ScrollView roomScroll;
    private EditText roomInput;
    private LinearLayout emojiPanel;
    private int lastMsgCount = -1;
    private static final int REQ_IMG = 101, REQ_FILE = 102, REQ_PROFILE = 103, REQ_OCR = 104, REQ_CAM = 105, REQ_IMPORT = 106, REQ_RESTORE = 107, REQ_LIB = 108;
    private final java.util.Map<String, android.graphics.Bitmap> bmpCache = new java.util.HashMap<>();
    private File camFile;

    private static final String[] EMOJIS = ("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😎 🤩 🥳 😏 😌 😴 🤔 🤗 🙄 😬 😢 😭 😤 😡 🥺 😱 😳 🤝 👍 👎 👏 🙌 🙏 💪 👌 ✌️ 🤞 👋 ✅ ❌ ⭕ ❗ ❓ 💯 🔥 ⭐ ✨ 🎉 🎊 💰 💳 🧾 📞 📱 💻 🖨️ 📦 🚚 🛠️ 🔧 📝 📎 📅 ⏰ 🏪 🍚 ☕ 🍺 ❤️ 💛 💙 💜 🖤 🤍 😷 🤒 🥱 😅 🫡 🫶").split(" ");

    private void leaveRoom() {
        inRoom = false;
        chatRoom = 0;
        msgBox = null;
        roomScroll = null;
        roomInput = null;
        emojiPanel = null;
        replyBar = null;
        replyTo = null;
        lastMsgCount = -1;
        buildMain();
    }

    private void hideKeyboard(View v) {
        try {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    /** 채팅방 전체 화면: 위에 방 이름, 가운데 대화, 맨 아래 입력줄이 고정된다. */
    private void showRoom() {
        final JSONObject room = store.byId("rooms", chatRoom);
        if (room == null) {
            leaveRoom();
            return;
        }
        inRoom = true;
        lastMsgCount = -1;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(0, Ui.dp(this, 40), 0, 0);

        LinearLayout hd = new LinearLayout(this);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 8));
        TextView back = Ui.text(this, "‹", 32, Ui.DEEP, true);
        back.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 4));
        back.setOnClickListener(v -> {
            hideKeyboard(v);
            leaveRoom();
        });
        hd.addView(back);
        TextView nm = Ui.text(this, roomTitle(room), 19, Ui.INK, true);
        nm.setSingleLine(true);
        hd.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView ren = Ui.text(this, "이름 변경", 13, Ui.GRAY, false);
        ren.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
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
                        showRoom();
                    }).show();
        });
        if (room.optBoolean("dm")) ren.setVisibility(View.GONE);
        hd.addView(ren);
        root.addView(hd);

        View line = new View(this);
        line.setBackgroundColor(Ui.FIELD);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        roomScroll = new ScrollView(this);
        roomScroll.setVerticalScrollBarEnabled(false);
        roomScroll.setBackgroundColor(Color.rgb(250, 248, 242));
        msgBox = new LinearLayout(this);
        msgBox.setOrientation(LinearLayout.VERTICAL);
        msgBox.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));
        roomScroll.addView(msgBox);
        root.addView(roomScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emojiPanel = buildEmojiPanel();
        emojiPanel.setVisibility(View.GONE);
        root.addView(emojiPanel);

        replyBar = new LinearLayout(this);
        replyBar.setGravity(Gravity.CENTER_VERTICAL);
        replyBar.setBackgroundColor(Ui.TINT);
        replyBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        replyTv = Ui.text(this, "", 13, Ui.DEEP, false);
        replyTv.setSingleLine(true);
        replyTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        replyBar.addView(replyTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView rx = Ui.text(this, "✕", 18, Ui.GRAY, true);
        rx.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        rx.setOnClickListener(v -> clearReply());
        replyBar.addView(rx);
        replyBar.setVisibility(View.GONE);
        root.addView(replyBar);
        replyTo = null;

        View line2 = new View(this);
        line2.setBackgroundColor(Ui.FIELD);
        root.addView(line2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.BOTTOM);
        in.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 14));
        in.setBackgroundColor(Color.WHITE);
        TextView plus = Ui.text(this, "+", 24, Ui.DEEP, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(Ui.round(Ui.TINT, 20, this));
        plus.setOnClickListener(v -> showAttachMenu());
        in.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        roomInput = new EditText(this);
        roomInput.setHint("메시지 입력");
        roomInput.setTextSize(16);
        roomInput.setTextColor(Ui.INK);
        roomInput.setHintTextColor(Ui.GRAY);
        roomInput.setBackground(Ui.round(Ui.FIELD, 20, this));
        roomInput.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        roomInput.setMaxLines(4);
        roomInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        roomInput.setText(draft);
        roomInput.setSelection(roomInput.getText().length());
        roomInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                draft = s.toString();
            }
        });
        roomInput.setOnClickListener(v -> emojiPanel.setVisibility(View.GONE));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ip.leftMargin = Ui.dp(this, 8);
        in.addView(roomInput, ip);
        TextView emo = Ui.text(this, "☺", 26, Ui.DEEP, false);
        emo.setGravity(Gravity.CENTER);
        emo.setOnClickListener(v -> {
            if (emojiPanel.getVisibility() == View.VISIBLE) {
                emojiPanel.setVisibility(View.GONE);
            } else {
                hideKeyboard(v);
                emojiPanel.setVisibility(View.VISIBLE);
                roomScroll.post(() -> roomScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
        in.addView(emo, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        Button send = Ui.button(this, "보내기", Ui.GOLD, Ui.DARK);
        send.setOnClickListener(v -> {
            String t = roomInput.getText().toString().trim();
            if (t.isEmpty()) return;
            sendMsg(t, "", "", "", 0, 0);
            draft = "";
            roomInput.setText("");
        });
        in.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));
        root.addView(in);

        if (wide()) {
            FrameLayout outer = new FrameLayout(this);
            outer.setBackgroundColor(Ui.CARD);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Ui.dp(this, MAX_CONTENT_DP), ViewGroup.LayoutParams.MATCH_PARENT);
            cp.gravity = Gravity.CENTER_HORIZONTAL;
            outer.addView(root, cp);
            setContentView(outer);
        } else {
            setContentView(root);
        }
        fillMsgs();
    }

    private static final String[] FRIENDS = {"나인이", "복이", "호야", "깍이", "도리"};
    private static final String[][] STK_CAPS = {
        {"신규계약 성공!", "축하축하~!", "대단해 대단해", "아 빡쳐!!", "정신 차려!", "힘내요 우리!", "POS 설치 중", "여보세요~ 나인입니다", "현장 AS 출동!", "공부하는 대표님", "굿샷~ 접대골프", "토스단말기 어떠세요", "회의 또 회의", "사장님 수다 삼매경", "수수료 입금 완료", "OK 접수 완료"},
        {"복 터졌다 계약!", "복 받으세요~", "캬 대단하다", "복이도 화나!", "정신 줄 잡아요", "힘내면 복와요", "랜선 공사 중", "전화 응대 중", "단말기 도색 중", "독서 삼매경", "차에서 꿀잠", "단말기 수리 중", "서류 접수했습니다", "돈복 터졌다", "점심 뭐 먹지", "복 감사합니다"},
        {"어흥! 계약 따냈다", "축하 어흥~", "대단하다 어흥", "어흥 빡쳤다!!", "정신 차려 어흥", "힘내라 어흥!", "AS 출동 어흥", "랜선 깔자 어흥", "테이블오더 설치", "호야 홀인원!", "토스 영업 어흥", "서류 작성 중", "보고서 쓰는 중", "퇴근하고 싶다", "죄송합니다...", "알겠습니다 어흥"},
        {"깍깍! 계약 성공", "까치가 물어왔다", "깍! 대단해", "깍깍 빡쳐!!", "깍! 정신 차려", "힘내 깍깍", "깍깍 전화 중", "상담원 연결 중", "블로그 작업 중", "보고서 쓰는 중", "깍깍 회의 중", "사장님 깍깍 수다", "서류 접수 완료", "엉엉 깍깍", "감사합니다 깍", "점심은 뭐 쪼지"},
        {"도리 계약 땄다!", "도토리 축하~", "도리도리 대단해", "도리 빡쳐!!", "정신 차려 도리", "힘내 도리도리", "쇼핑몰 작업 중", "서류 작성 중", "공부하는 도리", "POS 설치 중", "차에서 농땡이", "도리 골프 연습", "토스 영업 중", "월요일 싫어", "도토리값 입금", "도리 반성중"}};
    private static final String[] STK_OLD = {"화이팅!", "수고했어요", "최고예요!", "대박!", "감사합니다", "힘내세요!", "오늘도 굿!", "퇴근각", "확인했어요", "잠시만요", "계약 성공!", "같이 해요!"};
    private static final String[] REACTS = {"👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "👏"};
    private JSONObject replyTo = null;
    private LinearLayout replyBar;
    private TextView replyTv;

    /** 아래쪽 이모지·스티커 판: 위 탭(이모지 + 나인이 프렌즈 5마리)으로 바꾼다. */
    private LinearLayout buildEmojiPanel() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.CARD);
        final int n = FRIENDS.length + 1;
        final TextView[] tabv = new TextView[n];
        final View[] pages = new View[n];
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 4));
        hs.addView(tabs);
        box.addView(hs);
        FrameLayout body = new FrameLayout(this);

        ScrollView emojiSv = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 6));
        LinearLayout row = null;
        for (int i = 0; i < EMOJIS.length; i++) {
            if (i % 8 == 0) {
                row = new LinearLayout(this);
                grid.addView(row);
            }
            final String e = EMOJIS[i];
            TextView t = Ui.text(this, e, 26, Ui.INK, false);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                if (roomInput == null) return;
                int a = Math.max(0, roomInput.getSelectionStart());
                roomInput.getText().insert(a, e);
            });
            row.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1f));
        }
        emojiSv.addView(grid);
        pages[0] = emojiSv;
        for (int c = 0; c < FRIENDS.length; c++) {
            final int ci = c + 1;
            ScrollView sv = new ScrollView(this);
            LinearLayout sg = new LinearLayout(this);
            sg.setOrientation(LinearLayout.VERTICAL);
            sg.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 8));
            LinearLayout srow = null;
            for (int i = 0; i < STK_CAPS[c].length; i++) {
                if (i % 4 == 0) {
                    srow = new LinearLayout(this);
                    sg.addView(srow);
                }
                final int idx = i + 1;
                final String key = String.format(Locale.US, "c%d_%02d", ci, idx);
                ImageView iv = new ImageView(this);
                iv.setImageResource(stkRes(key));
                iv.setAdjustViewBounds(true);
                iv.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
                final String cap = STK_CAPS[c][i];
                iv.setOnClickListener(v -> {
                    sendMsg(cap, "stk", key, "", 0, 0);
                    emojiPanel.setVisibility(View.GONE);
                });
                srow.addView(iv, new LinearLayout.LayoutParams(0, Ui.dp(this, 90), 1f));
            }
            sv.addView(sg);
            pages[ci] = sv;
        }
        for (int i = 0; i < n; i++) {
            final int ti = i;
            TextView t = Ui.chip(this, i == 0 ? "이모지" : FRIENDS[i - 1], i == 0 ? Ui.GOLD : Ui.FIELD, i == 0 ? Ui.DARK : Ui.INK);
            tabv[i] = t;
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.rightMargin = Ui.dp(this, 8);
            tabs.addView(t, tp);
            body.addView(pages[i]);
            pages[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            t.setOnClickListener(v -> {
                for (int j = 0; j < n; j++) {
                    pages[j].setVisibility(j == ti ? View.VISIBLE : View.GONE);
                    tabv[j].setBackground(Ui.round(j == ti ? Ui.GOLD : Ui.FIELD, 10, this));
                }
            });
        }
        box.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 236)));
        return box;
    }

    /** 스티커 그림 찾기: 새 키("c2_05") 또는 예전 번호("7"). */
    private int stkRes(String key) {
        String nm = key.startsWith("c") ? "stk_" + key : String.format(Locale.US, "stk_%02d", Integer.parseInt(key));
        return getResources().getIdentifier(nm, "drawable", getPackageName());
    }

    private static String snippet(JSONObject m) {
        String k = m.optString("kind");
        if ("img".equals(k)) return "사진";
        if ("stk".equals(k)) return "스티커";
        if ("file".equals(k)) return "파일: " + m.optString("fname");
        String t = m.optString("text").replace('\n', ' ');
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    private void setReply(JSONObject m) {
        replyTo = m;
        if (replyBar == null) return;
        replyTv.setText(store.userName(m.optInt("who")) + "에게 답장  ·  " + snippet(m));
        replyBar.setVisibility(View.VISIBLE);
        if (roomInput != null) {
            roomInput.requestFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(roomInput, 0);
        }
    }

    private void clearReply() {
        replyTo = null;
        if (replyBar != null) replyBar.setVisibility(View.GONE);
    }

    private void showAttachMenu() {
        final String[] items = {"사진 보내기 (앨범)", "사진 찍어 보내기", "파일 보내기 (3MB까지)"};
        new AlertDialog.Builder(this).setTitle("보내기").setItems(items, (d, w) -> {
            try {
                if (w == 0) {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    startActivityForResult(Intent.createChooser(i, "사진 고르기"), REQ_IMG);
                } else if (w == 1) {
                    camFile = new File(Media.dir(this), "cam.jpg");
                    startActivityForResult(cameraIntent(camFile), REQ_CAM);
                } else {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(i, "파일 고르기"), REQ_FILE);
                }
            } catch (Exception e) {
                Ui.toast(this, "열 수 있는 앱이 없어요.");
            }
        }).show();
    }

    private Intent cameraIntent(File out) {
        Uri u = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", out);
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, u);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    private void sendMsg(String text, String kind, String blob, String fname, long size, int chunks) {
        JSONObject m = store.create("msgs");
        try {
            m.put("room", chatRoom);
            m.put("who", myId());
            m.put("text", text);
            m.put("ts", System.currentTimeMillis());
            JSONObject rm = store.byId("rooms", chatRoom);
            if (rm != null && rm.optJSONArray("mem") != null) m.put("mem", new JSONArray(rm.optJSONArray("mem").toString())); // 1:1 방이면 참가자 표시를 따라간다
            if (!kind.isEmpty()) {
                m.put("kind", kind);
                m.put("blob", blob);
                m.put("fname", fname);
                m.put("size", size);
                if (chunks > 0) m.put("chunks", chunks);
            }
            if (replyTo != null) {
                JSONObject r = new JSONObject();
                r.put("id", replyTo.optInt("id"));
                r.put("who", replyTo.optInt("who"));
                r.put("text", snippet(replyTo));
                m.put("rto", r);
            }
        } catch (Exception ignored) {
        }
        clearReply();
        store.save();
        fillMsgs();
    }

    /** 사진·파일을 서버 보관함(blobs)에 조각(600KB)으로 나눠 올린 뒤 대화에 올린다. */
    private void sendBlob(final String kind, final byte[] data, final String mime, final String fname) {
        final String id = String.valueOf(store.nextId());
        Media.write(Media.blobFile(this, id), data);
        final int n = Math.max(1, (data.length + Media.CHUNK - 1) / Media.CHUNK);
        final java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(n);
        final java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        final int room = chatRoom;
        Ui.toast(this, "보내는 중…");
        for (int i = 0; i < n; i++) {
            int from = i * Media.CHUNK, to = Math.min(data.length, from + Media.CHUNK);
            java.util.Map<String, Object> d = new java.util.HashMap<>();
            d.put("d", Media.b64(java.util.Arrays.copyOfRange(data, from, to)));
            d.put("mime", mime);
            d.put("name", fname);
            d.put("i", (long) i);
            d.put("n", (long) n);
            d.put("u", System.currentTimeMillis());
            d.put("by", (long) myId());
            Cloud.fs().collection("blobs").document(id + "_" + i).set(d).addOnSuccessListener(x -> {
                if (left.decrementAndGet() == 0 && !failed.get()) {
                    int keep = chatRoom;
                    chatRoom = room;
                    sendMsg(kind.equals("img") ? "사진" : fname, kind, id, fname, data.length, n);
                    chatRoom = keep;
                }
            }).addOnFailureListener(e -> {
                if (failed.compareAndSet(false, true)) Ui.toast(this, "보내지 못했어요. 인터넷을 확인해 주세요.");
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        final android.content.ContentResolver cr = getContentResolver();
        if (req == REQ_IMG || req == REQ_CAM) {
            final Uri u = req == REQ_CAM ? Uri.fromFile(camFile) : data.getData();
            if (u == null) return;
            new Thread(() -> {
                android.graphics.Bitmap b = Media.decode(cr, u, 1280);
                byte[] jp = b == null ? null : Media.jpeg(b, 600 * 1024);
                runOnUiThread(() -> {
                    if (jp == null) Ui.toast(this, "사진을 읽지 못했어요.");
                    else sendBlob("img", jp, "image/jpeg", "사진_" + new SimpleDateFormat("MMdd_HHmmss", Locale.KOREA).format(new Date()) + ".jpg");
                });
            }).start();
        } else if (req == REQ_IMPORT) {
            final Uri u = data.getData();
            if (u == null) return;
            new Thread(() -> {
                final byte[] raw = Media.readAll(cr, u, 8 * 1024 * 1024);
                runOnUiThread(() -> {
                    if (raw == null) Ui.toast(this, "파일을 읽지 못했어요. (8MB 이하만 가능)");
                    else previewImport(raw, Media.name(cr, u));
                });
            }).start();
        } else if (req == REQ_LIB) {
            final Uri u = data.getData();
            if (u == null) return;
            new Thread(() -> {
                final Object[] got = readLibFile(cr, u);
                runOnUiThread(() -> {
                    if (got[0] == null) Ui.toast(this, "파일을 읽지 못했어요. 3MB 이하만 올릴 수 있어요.");
                    else uploadLibFile((byte[]) got[0], (String) got[1], (String) got[2]);
                });
            }).start();
        } else if (req == REQ_RESTORE) {
            final Uri u = data.getData();
            if (u == null) return;
            new Thread(() -> {
                final byte[] raw = Media.readAll(cr, u, 40 * 1024 * 1024);
                runOnUiThread(() -> {
                    if (raw == null) {
                        Ui.toast(this, "파일을 읽지 못했어요.");
                        return;
                    }
                    try {
                        JSONObject o = new JSONObject(new String(raw, "UTF-8"));
                        JSONArray ms = o.optJSONArray("merchants");
                        if (ms == null) throw new Exception("no merchants");
                        restorePreview(ms, o.optBoolean("exact"), "선택한 파일");
                    } catch (Exception ex) {
                        Ui.toast(this, "백업 파일이 아니에요. '나인패밀리_백업_…json' 파일을 골라 주세요.");
                    }
                });
            }).start();
        } else if (req == REQ_FILE) {
            final Uri u = data.getData();
            if (u == null) return;
            new Thread(() -> {
                byte[] b = Media.readAll(cr, u, Media.MAX_FILE);
                final String nm = Media.name(cr, u);
                final String mime = cr.getType(u) == null ? "application/octet-stream" : cr.getType(u);
                runOnUiThread(() -> {
                    if (b == null) new AlertDialog.Builder(this).setTitle("파일이 너무 커요")
                            .setMessage("지금은 3MB 이하의 파일만 보낼 수 있어요. 사진은 자동으로 줄여서 보내니 사진으로 보내 주세요.").setPositiveButton("확인", null).show();
                    else sendBlob("file", b, mime, nm);
                });
            }).start();
        } else if (req == REQ_PROFILE) {
            final Uri u = data.getData();
            if (u == null) return;
            android.graphics.Bitmap b = Media.decode(cr, u, 512);
            byte[] jp = b == null ? null : Media.jpeg(Media.square(b, 256), 40 * 1024);
            if (jp == null) {
                Ui.toast(this, "사진을 읽지 못했어요.");
                return;
            }
            try {
                store.myProfile(myId()).put("pic", Media.b64(jp));
            } catch (Exception ignored) {
            }
            store.save();
            bmpCache.clear();
            Ui.toast(this, "프로필 사진을 바꿨어요.");
            if (inRoom) showRoom();
            else buildMain();
        } else if (req == REQ_OCR) {
            runOcr();
        }
    }

    private android.graphics.Bitmap thumb(File f, int maxSide) {
        String key = f.getName() + "@" + maxSide + "@" + f.length();
        android.graphics.Bitmap c = bmpCache.get(key);
        if (c != null) return c;
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
        o2.inSampleSize = sample;
        c = android.graphics.BitmapFactory.decodeFile(f.getPath(), o2);
        if (c != null) {
            if (bmpCache.size() > 40) bmpCache.clear();
            bmpCache.put(key, c);
        }
        return c;
    }

    /** 보관함에서 파일을 폰으로 받아 둔다(이미 있으면 바로 완료). chunks<=0 이면 0.7.0 이전의 한 덩어리 문서. */
    private void fetchBlob(final String id, final int chunks, final Runnable done) {
        final File f = Media.blobFile(this, id);
        if (f.exists() && f.length() > 0) {
            done.run();
            return;
        }
        final int n = Math.max(1, chunks);
        final byte[][] parts = new byte[n][];
        final java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(n);
        final java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            String docId = chunks <= 0 ? id : id + "_" + i;
            Cloud.fs().collection("blobs").document(docId).get().addOnSuccessListener(doc -> {
                String d = doc.getString("d");
                if (d == null) {
                    if (failed.compareAndSet(false, true)) Ui.toast(this, "파일을 찾지 못했어요.");
                    return;
                }
                parts[idx] = Media.unb64(d);
                if (left.decrementAndGet() == 0 && !failed.get()) {
                    int total = 0;
                    for (byte[] p : parts) total += p.length;
                    byte[] all = new byte[total];
                    int pos = 0;
                    for (byte[] p : parts) {
                        System.arraycopy(p, 0, all, pos, p.length);
                        pos += p.length;
                    }
                    Media.write(f, all);
                    done.run();
                }
            }).addOnFailureListener(e -> {
                if (failed.compareAndSet(false, true)) Ui.toast(this, "내려받지 못했어요. 인터넷을 확인해 주세요.");
            });
        }
    }

    private void openBlob(final String id, final int chunks, final String fname, final boolean isImg) {
        fetchBlob(id, chunks, () -> {
            try {
                String safe = fname.replaceAll("[^0-9A-Za-z가-힣._-]", "_");
                File out = new File(Media.dir(this), "open_" + id + "_" + safe);
                if (!out.exists()) {
                    byte[] b = Media.readAll(getContentResolver(), Uri.fromFile(Media.blobFile(this, id)), Integer.MAX_VALUE);
                    Media.write(out, b);
                }
                Uri u = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", out);
                String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(safe);
                String mime = isImg ? "image/jpeg" : android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext == null ? "" : ext.toLowerCase(Locale.ROOT));
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(u, mime == null ? "*/*" : mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "열기"));
            } catch (Exception e) {
                Ui.toast(this, "열 수 있는 앱이 없어요.");
            }
        });
    }

    private void showImage(final String id, final int chunks, final String fname) {
        fetchBlob(id, chunks, () -> {
            android.graphics.Bitmap b = thumb(Media.blobFile(this, id), 1600);
            if (b == null) {
                Ui.toast(this, "사진을 열지 못했어요.");
                return;
            }
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(b);
            iv.setAdjustViewBounds(true);
            iv.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            ScrollView sv = new ScrollView(this);
            sv.addView(iv);
            new AlertDialog.Builder(this).setView(sv).setNegativeButton("닫기", null)
                    .setPositiveButton("다른 앱으로 열기·저장", (d, w) -> openBlob(id, chunks, fname, true)).show();
        });
    }

    private String sizeText(long n) {
        return n >= 1024 * 1024 ? String.format(Locale.KOREA, "%.1fMB", n / 1048576.0) : Math.max(1, n / 1024) + "KB";
    }

    private void fillMsgs() {
        if (msgBox == null || roomScroll == null) return;
        boolean atBottom = lastMsgCount < 0 || roomScroll.getChildAt(0).getBottom() - (roomScroll.getHeight() + roomScroll.getScrollY()) < Ui.dp(this, 120);
        msgBox.removeAllViews();
        List<JSONObject> ms = new ArrayList<>();
        for (JSONObject m : store.list(store.arr("msgs"))) if (m.optInt("room") == chatRoom) ms.add(m);
        Collections.sort(ms, (a, b) -> Long.compare(a.optLong("ts"), b.optLong("ts")));
        if (ms.isEmpty()) {
            TextView e = Ui.text(this, "첫 메시지를 남겨 보세요.", 14, Ui.GRAY, false);
            e.setGravity(Gravity.CENTER);
            msgBox.addView(e, Ui.lp(this, 24));
        }
        SimpleDateFormat df = new SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREA);
        SimpleDateFormat dk = new SimpleDateFormat("yyyyMMdd", Locale.KOREA);
        SimpleDateFormat tf = new SimpleDateFormat("a h:mm", Locale.KOREA);
        String lastDay = "";
        JSONObject prev = null;
        for (final JSONObject m : ms) {
            Date dt = new Date(m.optLong("ts"));
            String day = dk.format(dt);
            if (!day.equals(lastDay)) {
                lastDay = day;
                TextView dl = Ui.chip(this, df.format(dt), Color.rgb(236, 232, 220), Ui.GRAY);
                LinearLayout.LayoutParams dp = Ui.lp(this, 14);
                dp.gravity = Gravity.CENTER_HORIZONTAL;
                dp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                msgBox.addView(dl, dp);
                prev = null;
            }
            final boolean mine = m.optInt("who") == myId();
            boolean same = prev != null && prev.optInt("who") == m.optInt("who")
                    && tf.format(new Date(prev.optLong("ts"))).equals(tf.format(dt));
            boolean nextSame = false;
            int idx = ms.indexOf(m);
            if (idx + 1 < ms.size()) {
                JSONObject nx = ms.get(idx + 1);
                nextSame = nx.optInt("who") == m.optInt("who") && dk.format(new Date(nx.optLong("ts"))).equals(day)
                        && tf.format(new Date(nx.optLong("ts"))).equals(tf.format(dt));
            }
            View bubble = buildBubble(m, mine);
            TextView tm = Ui.text(this, nextSame ? "" : tf.format(dt), 10, Ui.GRAY, false);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.BOTTOM);
            if (mine) {
                row.setGravity(Gravity.BOTTOM | Gravity.END);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tp.rightMargin = Ui.dp(this, 6);
                row.addView(tm, tp);
                row.addView(bubble);
            } else {
                if (same) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 40), 1));
                    LinearLayout col = new LinearLayout(this);
                    col.setGravity(Gravity.BOTTOM);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cp.leftMargin = Ui.dp(this, 8);
                    col.addView(bubble);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tp.leftMargin = Ui.dp(this, 6);
                    col.addView(tm, tp);
                    row.addView(col, cp);
                } else {
                    row.setGravity(Gravity.TOP);
                    final int who = m.optInt("who");
                    View av = avatarView(who, 40);
                    av.setOnClickListener(v -> showProfile(who));
                    row.addView(av);
                    LinearLayout col = new LinearLayout(this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.addView(Ui.text(this, store.userName(who), 12, Ui.GRAY, false));
                    LinearLayout bt = new LinearLayout(this);
                    bt.setGravity(Gravity.BOTTOM);
                    bt.addView(bubble);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tp.leftMargin = Ui.dp(this, 6);
                    bt.addView(tm, tp);
                    LinearLayout.LayoutParams bp = Ui.lp(this, 3);
                    bp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                    col.addView(bt, bp);
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cp.leftMargin = Ui.dp(this, 8);
                    row.addView(col, cp);
                }
            }
            msgBox.addView(row, Ui.lp(this, same ? 3 : 10));
            prev = m;
        }
        lastMsgCount = ms.size();
        if (atBottom) roomScroll.post(() -> roomScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void toggleReaction(JSONObject m, String emoji) {
        try {
            JSONObject r = m.optJSONObject("react");
            if (r == null) {
                r = new JSONObject();
                m.put("react", r);
            }
            String k = String.valueOf(myId());
            if (emoji.equals(r.optString(k))) r.remove(k);
            else r.put(k, emoji);
        } catch (Exception ignored) {
        }
        store.save();
        fillMsgs();
    }

    /** 말풍선 속 링크 열기: 하나면 바로, 여러 개면 고르게. */
    private void openLinks(final java.util.List<String> urls) {
        if (urls.size() == 1) {
            openUrl(urls.get(0));
            return;
        }
        new AlertDialog.Builder(this).setTitle("링크 열기")
                .setItems(urls.toArray(new String[0]), (d, w) -> openUrl(urls.get(w))).show();
    }

    private void openUrl(String u) {
        if (!u.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) u = "https://" + u;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception e) {
            Ui.toast(this, "링크를 열 수 없어요.");
        }
    }

    // ======================= v0.10: 입금확인 · 비상연락망 · 알림 · 백업 =======================
    private static final String[] DEP_KINDS = {"판매대금", "미납금", "정보변경수수료", "기타"};

    /** 이름이 맞는 채팅방에 시스템 글(내 이름)을 올린다. 방이 없으면 만든다. */
    private void postToRoom(String roomName, String text) {
        int rid = 0;
        for (JSONObject r : store.list(store.arr("rooms"))) if (roomName.equals(r.optString("name"))) rid = r.optInt("id");
        try {
            if (rid == 0) {
                JSONObject r = store.create("rooms");
                r.put("name", roomName);
                r.put("by", myId());
                rid = r.optInt("id");
            }
            JSONObject m = store.create("msgs");
            m.put("room", rid);
            m.put("who", myId());
            m.put("text", text);
            m.put("ts", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        store.save();
    }

    private void renderDeposits() {
        content.addView(Ui.text(this, "입금 확인", 22, Ui.INK, true));
        content.addView(Ui.text(this, "입금 내역을 올리면 '입금확인' 채팅방에도 자동으로 알림이 올라가요. 오른쪽 아래 + 로 올립니다.", 13, Ui.GRAY, false), Ui.lp(this, 4));
        List<JSONObject> list = store.list(store.arr("deposits"));
        Collections.sort(list, (a, b) -> b.optString("t").compareTo(a.optString("t")));
        if (list.isEmpty()) content.addView(Ui.text(this, "올라온 입금 내역이 없습니다.", 15, Ui.GRAY, false), Ui.lp(this, 24));
        long sum = 0;
        for (final JSONObject d : list) {
            LinearLayout c = Ui.card(this);
            boolean ok = d.optBoolean("ok");
            c.addView(Ui.text(this, d.optString("kind") + "  " + won(d.optLong("amount")) + (ok ? "  ✔ 확인됨" : "  · 확인 전"), 16, ok ? Ui.GREEN : Ui.INK, true));
            String who = d.optString("mname");
            c.addView(Ui.text(this, (who.isEmpty() ? "(가맹점 미지정)" : who) + (d.optString("memo").isEmpty() ? "" : " · " + d.optString("memo")), 14, Ui.INK, false), Ui.lp(this, 2));
            c.addView(Ui.text(this, d.optString("t") + " · 올린 사람 " + store.userName(d.optInt("by")) + (ok ? " · 확인 " + store.userName(d.optInt("okby")) : ""), 12, Ui.GRAY, false), Ui.lp(this, 2));
            c.setOnClickListener(v -> showDeposit(d));
            content.addView(c, Ui.lp(this, 10));
            if (!ok) sum += d.optLong("amount");
        }
    }

    private void showDeposit(final JSONObject d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.optString("kind")).append(" ").append(won(d.optLong("amount"))).append("\n가맹점: ").append(d.optString("mname").isEmpty() ? "-" : d.optString("mname"))
                .append("\n메모: ").append(d.optString("memo").isEmpty() ? "-" : d.optString("memo")).append("\n올린 사람: ").append(store.userName(d.optInt("by"))).append(" · ").append(d.optString("t"));
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("입금 내역").setMessage(sb.toString()).setNegativeButton("닫기", null);
        if (!d.optBoolean("ok")) {
            b.setPositiveButton("확인 처리", (x, w) -> {
                try {
                    d.put("ok", true);
                    d.put("okby", myId());
                } catch (Exception ignored) {
                }
                store.save();
                postToRoom("입금확인", "✔ 입금 확인 완료 · " + d.optString("kind") + " " + won(d.optLong("amount")) + (d.optString("mname").isEmpty() ? "" : " · " + d.optString("mname")) + " (" + store.userName(myId()) + ")");
                render();
            });
        }
        b.show();
    }

    private void showAddDeposit() {
        final JSONObject[] mer = new JSONObject[1];
        LinearLayout f = form();
        final Spinner kind = new Spinner(this);
        kind.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DEP_KINDS));
        final EditText amt = Ui.field(this, "금액 (원, 숫자)", false);
        amt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText memo = Ui.field(this, "메모 (기타일 때 내용, 입금자명 등)", false);
        final TextView merInfo = Ui.text(this, "", 13, Ui.INK, false);
        Button pick = Ui.button(this, "가맹점 조회·선택 (어느 가맹점 입금인지)", Ui.TINT, Ui.DEEP);
        pick.setOnClickListener(v -> pickMerchant(m -> {
            mer[0] = m;
            merInfo.setText(m.optString("name"));
        }));
        f.addView(kind);
        f.addView(amt);
        f.addView(pick, Ui.lp(this, 10));
        f.addView(merInfo, Ui.lp(this, 6));
        f.addView(memo);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        new AlertDialog.Builder(this).setTitle("입금 올리기").setView(sv).setNegativeButton("취소", null)
                .setPositiveButton("올리기", (x, w) -> {
                    long a = parseWon(amt);
                    if (a <= 0) {
                        Ui.toast(this, "금액을 입력해 주세요.");
                        return;
                    }
                    String k = DEP_KINDS[kind.getSelectedItemPosition()];
                    JSONObject d = store.create("deposits");
                    try {
                        d.put("kind", k);
                        d.put("amount", a);
                        d.put("memo", memo.getText().toString().trim());
                        d.put("merchant", mer[0] == null ? 0 : mer[0].optInt("id"));
                        d.put("mname", mer[0] == null ? "" : mer[0].optString("name"));
                        d.put("by", myId());
                        d.put("t", Store.now());
                        d.put("ok", false);
                    } catch (Exception ignored) {
                    }
                    store.save();
                    postToRoom("입금확인", "💰 입금 확인 요청 · " + k + " " + won(a) + (mer[0] == null ? "" : " · " + mer[0].optString("name"))
                            + (memo.getText().toString().trim().isEmpty() ? "" : " · " + memo.getText().toString().trim()) + " (" + store.userName(myId()) + ")");
                    Ui.toast(this, "올렸어요. '입금확인' 채팅방에도 알렸습니다.");
                    render();
                }).show();
    }

    private String contactQuery = "";

    private void renderContacts() {
        content.addView(Ui.text(this, "비상연락망", 22, Ui.INK, true));
        content.addView(Ui.text(this, "번호를 누르면 전화 걸기 화면이 열려요. 길게 누르면 수정·삭제. 오른쪽 아래 + 로 추가합니다.", 13, Ui.GRAY, false), Ui.lp(this, 4));
        final EditText q = Ui.field(this, "🔍 이름·구분·번호 검색 (초성 가능)", false);
        q.setText(contactQuery);
        content.addView(q, Ui.lp(this, 8));
        q.setOnEditorActionListener((v, a, ev) -> {
            contactQuery = q.getText().toString();
            render();
            return true;
        });
        List<JSONObject> list = store.list(store.arr("contacts"));
        Collections.sort(list, (a, b) -> {
            int c = a.optString("cat").compareTo(b.optString("cat"));
            return c != 0 ? c : a.optString("name").compareTo(b.optString("name"));
        });
        if (list.isEmpty()) content.addView(Ui.text(this, "등록된 연락처가 없습니다. + 를 눌러 본사·카드사·밴사·긴급 연락처를 넣어 두세요.", 15, Ui.GRAY, false), Ui.lp(this, 24));
        String cat = null;
        for (final JSONObject c : list) {
            if (!contactQuery.isEmpty() && !Kor.match(c.optString("name") + " " + c.optString("cat") + " " + c.optString("phone") + " " + c.optString("memo"), contactQuery)) continue;
            if (!c.optString("cat").equals(cat)) {
                cat = c.optString("cat");
                content.addView(Ui.text(this, cat.isEmpty() ? "기타" : cat, 14, Ui.DEEP, true), Ui.lp(this, 14));
            }
            LinearLayout row = Ui.card(this);
            row.addView(Ui.text(this, c.optString("name"), 16, Ui.INK, true));
            row.addView(Ui.text(this, "📞 " + c.optString("phone") + (c.optString("memo").isEmpty() ? "" : " · " + c.optString("memo")), 14, Ui.DEEP, false), Ui.lp(this, 2));
            row.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.optString("phone").replaceAll("[^0-9+*#]", ""))));
                } catch (Exception e) {
                    Ui.toast(this, "전화를 걸 수 없어요.");
                }
            });
            row.setOnLongClickListener(v -> {
                showEditContact(c);
                return true;
            });
            content.addView(row, Ui.lp(this, 8));
        }
    }

    private void showEditContact(final JSONObject c) {
        LinearLayout f = form();
        final EditText name = Ui.field(this, "이름 (필수, 예: 본사 총무)", false);
        final EditText cat = Ui.field(this, "구분 (예: 본사, 카드사, 밴사, 직원, 긴급)", false);
        final EditText ph = Ui.field(this, "전화번호 (필수)", false);
        ph.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        final EditText memo = Ui.field(this, "메모 (선택)", false);
        if (c != null) {
            name.setText(c.optString("name"));
            cat.setText(c.optString("cat"));
            ph.setText(c.optString("phone"));
            memo.setText(c.optString("memo"));
        }
        f.addView(name);
        f.addView(cat);
        f.addView(ph);
        f.addView(memo);
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle(c == null ? "연락처 추가" : "연락처 수정").setView(sv).setNegativeButton("취소", null)
                .setPositiveButton("저장", (x, w) -> {
                    if (name.getText().toString().trim().isEmpty() || ph.getText().toString().trim().isEmpty()) {
                        Ui.toast(this, "이름과 전화번호를 입력해 주세요.");
                        return;
                    }
                    JSONObject o = c != null ? c : store.create("contacts");
                    try {
                        o.put("name", name.getText().toString().trim());
                        o.put("cat", cat.getText().toString().trim());
                        o.put("phone", ph.getText().toString().trim());
                        o.put("memo", memo.getText().toString().trim());
                    } catch (Exception ignored) {
                    }
                    store.save();
                    render();
                });
        if (c != null) {
            b.setNeutralButton("삭제", (x, w) -> new AlertDialog.Builder(this).setTitle("삭제할까요?").setMessage(c.optString("name"))
                    .setNegativeButton("취소", null).setPositiveButton("삭제", (y, z) -> {
                        try {
                            c.put("deleted", true);
                        } catch (Exception ignored) {
                        }
                        store.save();
                        render();
                    }).show());
        }
        b.show();
    }

    private int notifySeq = 1;

    /** 새 업무·채팅·공지·입금이 올라오면 폰 알림(앱이 켜져 있거나 백그라운드로 살아 있을 때). */
    private void setupNotify() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel("work", "업무·채팅 알림", android.app.NotificationManager.IMPORTANCE_DEFAULT);
                ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            }
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 201);
            }
        } catch (Exception ignored) {
        }
        store.notifier = (coll, o) -> {
            int me2 = myId();
            String title = null, body = null;
            if ("tasks".equals(coll) && o.optInt("assignee") == me2 && o.optInt("by", o.optInt("author")) != me2) {
                title = "새 업무가 지시됐어요";
                body = o.optString("title");
            } else if ("msgs".equals(coll) && o.optInt("who") != me2 && o.optInt("ts", 1) != 0 && System.currentTimeMillis() - o.optLong("ts") < 120000) {
                title = store.userName(o.optInt("who"));
                body = snippet(o);
            } else if ("library".equals(coll) && o.optInt("who") != me2) {
                title = "자료실에 새 자료";
                body = o.optString("title");
            } else if ("notices".equals(coll) && o.optInt("author") != me2) {
                title = "새 공지";
                body = o.optString("title");
            } else if ("deposits".equals(coll) && o.optInt("by") != me2) {
                title = "입금 확인 요청";
                body = o.optString("kind") + " " + won(o.optLong("amount")) + " " + o.optString("mname");
            }
            if (title == null) return;
            try {
                Intent it = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
                android.app.Notification.Builder nb = android.os.Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(this, "work") : new android.app.Notification.Builder(this);
                nb.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true);
                ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(notifySeq++, nb.build());
            } catch (Exception ignored) {
            }
        };
    }

    /** 관리자: 서버에서 받은 전체 자료를 파일(JSON)로 내려받아 보관한다. */
    private void exportBackup() {
        try {
            String name = "나인패밀리_백업_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(new java.util.Date()) + ".json";
            File out = new File(Media.dir(this), name);
            Media.write(out, store.dumpJson().getBytes("UTF-8"));
            Uri u = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".files", out);
            Intent it = new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "백업 파일 보관(드라이브·카톡 나에게 등)"));
        } catch (Exception e) {
            Ui.toast(this, "백업 파일을 만들 수 없어요.");
        }
    }


    // ======================= v0.11: 가맹점 엑셀·CSV 일괄 가져오기 =======================
    private void startMerchantImport() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream"});
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(i, "가맹점 엑셀·CSV 고르기"), REQ_IMPORT);
    }

    private JSONObject findMerchant(String ecode, String bizDigits, String name) {
        List<JSONObject> all = store.list(store.arr("merchants"));
        if (!ecode.isEmpty()) for (JSONObject m : all) if (ecode.equals(m.optString("ecode"))) return m;
        if (bizDigits.length() == 10) for (JSONObject m : all) if (bizDigits.equals(m.optString("bizno").replaceAll("[^0-9]", ""))) return m;
        if (!name.isEmpty()) for (JSONObject m : all) if (name.equals(m.optString("name").trim())) return m;
        return null;
    }

    private void previewImport(byte[] raw, String fname) {
        final List<List<String>> rows;
        try {
            rows = MerchantImport.read(raw, fname);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("읽을 수 없어요").setMessage(e.getMessage() == null ? "파일 형식을 확인해 주세요." : e.getMessage()).setPositiveButton("확인", null).show();
            return;
        }
        Object[] h = MerchantImport.findHeader(rows);
        if (h == null) {
            new AlertDialog.Builder(this).setTitle("머리글을 못 찾았어요").setMessage("첫 줄(머리글)에 '거래처명' 또는 '상호'가 있어야 해요. 사업자등록번호·대표자·전화·핸드폰·주소 열도 있으면 함께 가져옵니다.").setPositiveButton("확인", null).show();
            return;
        }
        final int hr = (Integer) h[0];
        @SuppressWarnings("unchecked") final java.util.Map<String, Integer> map = (java.util.Map<String, Integer>) h[1];
        int add = 0, upd = 0, skip = 0;
        final List<List<String>> data = new ArrayList<>();
        for (int i = hr + 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            String nm = MerchantImport.cell(r, map, "name");
            if (nm.isEmpty()) {
                skip++;
                continue;
            }
            data.add(r);
            JSONObject ex = findMerchant(MerchantImport.cell(r, map, "ecode"), MerchantImport.cell(r, map, "bizno").replaceAll("[^0-9]", ""), nm);
            if (ex == null) add++;
            else upd++;
        }
        StringBuilder found = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> en : map.entrySet()) found.append(en.getKey()).append(" ← ").append(rows.get(hr).get(en.getValue())).append("\n");
        final int fa = add, fu = upd;
        new AlertDialog.Builder(this).setTitle("가져오기 미리보기")
                .setMessage("새로 등록 " + add + "곳\n기존 가맹점 갱신 " + upd + "곳\n건너뜀(상호 없음) " + skip + "곳\n\n[읽은 열]\n" + found
                        + "\n※ 같은 거래처코드·사업자번호·상호가 있으면 새로 만들지 않고, 파일에 값이 있는 칸만 덮어씁니다(빈 칸은 그대로 둠).\n※ 가져오기 직전 상태를 자동 저장해 두므로 언제든 되돌릴 수 있어요.")
                .setNegativeButton("취소", null)
                .setPositiveButton("가져오기", (d, w) -> applyImport(data, map, fa, fu)).show();
    }

    private void applyImport(List<List<String>> data, java.util.Map<String, Integer> map, int add, int upd) {
        if (snapshotMerchants("가져오기전") == null) { // 되돌릴 기준점을 못 만들면 시작하지 않는다
            new AlertDialog.Builder(this).setTitle("가져오기를 멈췄어요").setMessage("되돌리기용 백업을 만들지 못했어요. 폰 저장 공간을 확인한 뒤 다시 시도해 주세요.").setPositiveButton("확인", null).show();
            return;
        }
        for (List<String> r : data) {
            String nm = MerchantImport.cell(r, map, "name");
            String biz = MerchantImport.cell(r, map, "bizno");
            String ec = MerchantImport.cell(r, map, "ecode");
            JSONObject m = findMerchant(ec, biz.replaceAll("[^0-9]", ""), nm);
            boolean isNew = m == null;
            if (isNew) m = store.create("merchants");
            try {
                put(m, "name", nm, false);
                if (!biz.isEmpty()) m.put("bizno", MerchantImport.bizFmt(biz));
                put(m, "ecode", ec, false);
                put(m, "owner", MerchantImport.cell(r, map, "owner"), false);
                put(m, "phone", MerchantImport.cell(r, map, "phone"), false);
                put(m, "mobile", MerchantImport.cell(r, map, "mobile"), false);
                if (m.optString("phone").isEmpty() && !m.optString("mobile").isEmpty()) m.put("phone", m.optString("mobile"));
                put(m, "addr", MerchantImport.cell(r, map, "addr"), false);
                int g = MerchantImport.gradeOf(MerchantImport.cell(r, map, "grade"));
                if (g >= 0) m.put("grade", g);
                else if (isNew) m.put("grade", 5);
                String memo = MerchantImport.cell(r, map, "memo");
                String bt = (MerchantImport.cell(r, map, "biztype") + " " + MerchantImport.cell(r, map, "bizitem")).trim();
                if (isNew) m.put("memo", ((memo.isEmpty() ? "" : memo + " · ") + (bt.isEmpty() ? "" : "업태/종목 " + bt)).trim());
                else if (!memo.isEmpty()) m.put("memo", memo);
                if (isNew) m.put("deleted", false);
            } catch (Exception ignored) {
            }
        }
        store.save();
        Ui.toast(this, "가맹점 " + add + "곳 등록, " + upd + "곳 갱신했어요. 잘못되면 가맹점 → 🛡 가맹점 보호·복원에서 되돌릴 수 있어요.");
        render();
    }

    private static void put(JSONObject m, String k, String v, boolean force) throws Exception {
        if (!v.isEmpty() || force) m.put(k, v);
    }


    // ======================= v0.13: 자료실 =======================
    private static final String[] LIB_CATS = {"양식·서식", "계약·약정", "가맹점 안내", "교육·매뉴얼", "사진·홍보물", "기타"};
    private String libCat = "", libQuery = "";
    private final List<JSONObject> libPending = new ArrayList<>();
    private int libUploading = 0;
    private TextView libFilesTv;

    private static String sizeText(long n) {
        if (n >= 1024 * 1024) return String.format(Locale.US, "%.1fMB", n / 1048576.0);
        return Math.max(1, n / 1024) + "KB";
    }

    private void renderLibrary() {
        content.addView(Ui.text(this, "자료실", 22, Ui.INK, true));
        content.addView(Ui.text(this, "양식·계약서·안내문 같은 파일을 올려 두고 직원 모두가 내려받아 쓰는 곳이에요. 오른쪽 아래 + 로 올립니다.", 13, Ui.GRAY, false), Ui.lp(this, 4));
        final EditText q = Ui.field(this, "🔍 제목·설명·파일 이름 검색 (초성 가능)", false);
        q.setText(libQuery);
        content.addView(q, Ui.lp(this, 8));
        q.setOnEditorActionListener((v, a, ev) -> {
            libQuery = q.getText().toString();
            render();
            return true;
        });
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        final List<String> labels = new ArrayList<>();
        labels.add("전체");
        labels.addAll(java.util.Arrays.asList(LIB_CATS));
        for (final String lb : labels) {
            boolean on = lb.equals("전체") ? libCat.isEmpty() : lb.equals(libCat);
            TextView t = Ui.text(this, lb, 13, on ? Ui.DARK : Ui.INK, true);
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            t.setBackground(Ui.round(on ? Ui.GOLD : Ui.FIELD, 16, this));
            t.setOnClickListener(v -> {
                libCat = lb.equals("전체") ? "" : lb;
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            chips.addView(t, lp);
        }
        hs.addView(chips);
        content.addView(hs, Ui.lp(this, 8));
        List<JSONObject> list = store.list(store.arr("library"));
        Collections.sort(list, (a, b) -> b.optString("created").compareTo(a.optString("created")));
        int shown = 0;
        for (final JSONObject p : list) {
            if (!libCat.isEmpty() && !libCat.equals(p.optString("cat"))) continue;
            JSONArray fs = p.optJSONArray("files");
            StringBuilder names = new StringBuilder();
            if (fs != null) for (int i = 0; i < fs.length(); i++) names.append(fs.optJSONObject(i).optString("fname")).append(' ');
            if (!libQuery.isEmpty() && !Kor.match(p.optString("title") + " " + p.optString("desc") + " " + names + store.userName(p.optInt("who")), libQuery)) continue;
            shown++;
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, p.optString("title"), 16, Ui.INK, true));
            c.addView(Ui.text(this, p.optString("cat") + " · " + store.userName(p.optInt("who")) + " · " + p.optString("created"), 12, Ui.GRAY, false), Ui.lp(this, 2));
            if (!p.optString("desc").isEmpty()) {
                TextView d = Ui.text(this, p.optString("desc"), 14, Ui.INK, false);
                d.setMaxLines(2);
                d.setEllipsize(android.text.TextUtils.TruncateAt.END);
                c.addView(d, Ui.lp(this, 4));
            }
            if (fs != null && fs.length() > 0) c.addView(Ui.text(this, "📎 파일 " + fs.length() + "개", 13, Ui.DEEP, true), Ui.lp(this, 4));
            c.setOnClickListener(v -> showLibraryPost(p));
            content.addView(c, Ui.lp(this, 8));
        }
        if (shown == 0) content.addView(Ui.text(this, list.isEmpty() ? "아직 올라온 자료가 없어요. + 를 눌러 첫 자료를 올려 보세요." : "찾는 자료가 없어요.", 15, Ui.GRAY, false), Ui.lp(this, 24));
    }

    private void showLibraryPost(final JSONObject p) {
        LinearLayout l = form();
        l.addView(Ui.text(this, p.optString("title"), 19, Ui.INK, true));
        l.addView(Ui.text(this, p.optString("cat") + " · " + store.userName(p.optInt("who")) + " · " + p.optString("created"), 12, Ui.GRAY, false), Ui.lp(this, 2));
        if (!p.optString("desc").isEmpty()) {
            TextView d = Ui.text(this, p.optString("desc"), 15, Ui.INK, false);
            d.setLineSpacing(0, 1.25f);
            d.setTextIsSelectable(true);
            android.text.util.Linkify.addLinks(d, android.text.util.Linkify.WEB_URLS);
            l.addView(d, Ui.lp(this, 10));
        }
        JSONArray fs = p.optJSONArray("files");
        if (fs != null && fs.length() > 0) {
            l.addView(Ui.text(this, "첨부 파일 (눌러서 열기·저장)", 13, Ui.GRAY, true), Ui.lp(this, 14));
            for (int i = 0; i < fs.length(); i++) {
                final JSONObject f = fs.optJSONObject(i);
                TextView t = Ui.text(this, "📎 " + f.optString("fname") + "  (" + sizeText(f.optLong("size")) + ")", 15, Ui.DEEP, true);
                t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
                t.setOnClickListener(v -> {
                    Ui.toast(this, "파일을 받는 중…");
                    openBlob(f.optString("blob"), f.optInt("chunks"), f.optString("fname"), false);
                });
                l.addView(t);
            }
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setView(sv).setNegativeButton("닫기", null);
        if (p.optInt("who") == myId() || can(Perm.STAFF_MANAGE)) {
            b.setPositiveButton("수정", (d, w) -> showEditLibrary(p));
            b.setNeutralButton("삭제", (d, w) -> new AlertDialog.Builder(this).setTitle("'" + p.optString("title") + "' 삭제할까요?")
                    .setMessage("자료실 목록에서 사라져요. 서버에는 기록이 보관됩니다.").setNegativeButton("취소", null)
                    .setPositiveButton("삭제", (d2, w2) -> {
                        try {
                            p.put("deleted", true);
                        } catch (Exception ignored) {
                        }
                        store.save();
                        Ui.toast(this, "삭제했어요.");
                        render();
                    }).show());
        }
        b.show();
    }

    private void showEditLibrary(final JSONObject p) {
        LinearLayout f = form();
        final EditText title = Ui.field(this, "제목 (필수, 예: 가맹점 계약서 양식)", false);
        final EditText desc = Ui.field(this, "설명 (선택)", false);
        desc.setSingleLine(false);
        desc.setMinLines(3);
        final Spinner cat = spinner(LIB_CATS);
        if (p != null) {
            title.setText(p.optString("title"));
            desc.setText(p.optString("desc"));
            for (int i = 0; i < LIB_CATS.length; i++) if (LIB_CATS[i].equals(p.optString("cat"))) cat.setSelection(i);
        }
        f.addView(title);
        f.addView(desc);
        f.addView(Ui.text(this, "분류", 13, Ui.GRAY, true), Ui.lp(this, 10));
        f.addView(cat);
        if (p == null) {
            libPending.clear();
            libUploading = 0;
            libFilesTv = Ui.text(this, "첨부 파일 없음", 14, Ui.GRAY, false);
            Button add = Ui.button(this, "📎 파일 추가 (사진·문서, 3MB까지)", Ui.TINT, Ui.DEEP);
            add.setOnClickListener(v -> {
                if (libPending.size() + libUploading >= 5) {
                    Ui.toast(this, "한 글에 파일은 5개까지 올릴 수 있어요.");
                    return;
                }
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(i, "올릴 파일 고르기"), REQ_LIB);
                } catch (Exception e) {
                    Ui.toast(this, "열 수 있는 앱이 없어요.");
                }
            });
            f.addView(add, Ui.lp(this, 12));
            f.addView(libFilesTv, Ui.lp(this, 6));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(f);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle(p == null ? "자료 올리기" : "자료 수정").setView(sv)
                .setNegativeButton("취소", (d, w) -> libFilesTv = null).setPositiveButton(p == null ? "올리기" : "저장", null).create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            if (t.isEmpty()) {
                Ui.toast(this, "제목을 입력해 주세요.");
                return;
            }
            if (p == null && libUploading > 0) {
                Ui.toast(this, "파일을 올리는 중이에요. 잠시 뒤 다시 눌러 주세요.");
                return;
            }
            JSONObject o = p != null ? p : store.create("library");
            try {
                o.put("title", t);
                o.put("desc", desc.getText().toString().trim());
                o.put("cat", (String) cat.getSelectedItem());
                if (p == null) {
                    o.put("who", myId());
                    o.put("files", new JSONArray(libPending.toString()));
                }
            } catch (Exception ignored) {
            }
            store.save();
            libFilesTv = null;
            dlg.dismiss();
            Ui.toast(this, p == null ? "자료를 올렸어요." : "저장했어요.");
            render();
        });
    }

    private void refreshLibFiles() {
        if (libFilesTv == null) return;
        StringBuilder sb = new StringBuilder();
        for (JSONObject f : libPending) sb.append("📎 ").append(f.optString("fname")).append("  (").append(sizeText(f.optLong("size"))).append(")\n");
        if (libUploading > 0) sb.append("올리는 중… ").append(libUploading).append("개");
        libFilesTv.setText(sb.length() == 0 ? "첨부 파일 없음" : sb.toString().trim());
    }

    /** 고른 파일을 읽는다. 사진은 줄여서, 그 밖의 파일은 3MB까지 그대로. {바이트(없으면 null), 형식, 이름} */
    private Object[] readLibFile(android.content.ContentResolver cr, Uri u) {
        String mime = cr.getType(u);
        String name = Media.name(cr, u);
        if (name == null || name.isEmpty()) name = "파일";
        byte[] b;
        if (mime != null && mime.startsWith("image/")) {
            android.graphics.Bitmap bm = Media.decode(cr, u, 1600);
            b = bm == null ? null : Media.jpeg(bm, 900 * 1024);
            mime = "image/jpeg";
            if (!name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg)$")) name = name.replaceAll("\\.[A-Za-z0-9]+$", "") + ".jpg";
        } else {
            b = Media.readAll(cr, u, Media.MAX_FILE);
            if (mime == null) mime = "application/octet-stream";
        }
        return new Object[]{b, mime, name};
    }

    /** 파일을 서버 보관함(blobs)에 조각으로 올리고, 다 올라가면 첨부 목록에 넣는다. */
    private void uploadLibFile(final byte[] data, final String mime, final String fname) {
        final String id = String.valueOf(store.nextId());
        Media.write(Media.blobFile(this, id), data);
        final int n = Math.max(1, (data.length + Media.CHUNK - 1) / Media.CHUNK);
        final java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(n);
        final java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        libUploading++;
        refreshLibFiles();
        for (int i = 0; i < n; i++) {
            int from = i * Media.CHUNK, to = Math.min(data.length, from + Media.CHUNK);
            java.util.Map<String, Object> d = new java.util.HashMap<>();
            d.put("d", Media.b64(java.util.Arrays.copyOfRange(data, from, to)));
            d.put("mime", mime);
            d.put("name", fname);
            d.put("i", (long) i);
            d.put("n", (long) n);
            d.put("u", System.currentTimeMillis());
            d.put("by", (long) myId());
            Cloud.fs().collection("blobs").document(id + "_" + i).set(d).addOnSuccessListener(x -> {
                if (left.decrementAndGet() == 0 && !failed.get()) {
                    try {
                        JSONObject f = new JSONObject();
                        f.put("blob", id);
                        f.put("chunks", n);
                        f.put("fname", fname);
                        f.put("size", data.length);
                        libPending.add(f);
                    } catch (Exception ignored) {
                    }
                    libUploading = Math.max(0, libUploading - 1);
                    refreshLibFiles();
                }
            }).addOnFailureListener(e -> {
                if (failed.compareAndSet(false, true)) {
                    libUploading = Math.max(0, libUploading - 1);
                    refreshLibFiles();
                    Ui.toast(this, "올리지 못했어요. 인터넷을 확인해 주세요.");
                }
            });
        }
    }

    // ======================= v0.12: 가맹점 보호 장치 · 1:1 비공개 대화 =======================
    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(new Date());
    }

    /** 되돌리기 기준점: 지금 가맹점 전체(삭제 표시된 것 포함)를 폰 안 파일로 저장한다. 실패하면 null. */
    private File snapshotMerchants(String why) {
        try {
            JSONObject o = new JSONObject();
            o.put("kind", "merchants-snapshot");
            o.put("exact", true);
            o.put("why", why);
            o.put("at", Store.now());
            o.put("merchants", store.merchantsCopy());
            File f = Backups.save(this, "snap_" + stamp() + "_" + why + ".json", o.toString());
            Backups.prune(this, "snap_", 30);
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** 관리자 폰이 앱을 열면 하루 한 번(이후에도 자료가 늘면 갱신) 전체 자료를 폰 안에 14일치 보관한다. */
    private void autoBackup() {
        try {
            if (me == null || !can(Perm.STAFF_MANAGE)) return;
            int cnt = store.arr("merchants").length();
            if (cnt == 0 && store.users().length() == 0) return; // 아직 서버에서 못 받은 빈 상태는 저장하지 않는다
            android.content.SharedPreferences sp = getSharedPreferences("workapp", MODE_PRIVATE);
            String day = new SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(new Date());
            int prior = sp.getInt("bk_cnt_" + day, -1);
            long last = sp.getLong("bk_t_" + day, 0);
            if (prior > cnt) return; // 오늘 더 많은 자료로 저장한 백업이 있으면 덮지 않는다
            if (prior == cnt && System.currentTimeMillis() - last < 3 * 3600 * 1000L) return;
            Backups.save(this, "auto_" + day + ".json", store.dumpCompact());
            sp.edit().putInt("bk_cnt_" + day, cnt).putLong("bk_t_" + day, System.currentTimeMillis()).apply();
            Backups.prune(this, "auto_", 14);
        } catch (Exception ignored) {
        }
    }

    private void showSafetyMenu() {
        if (!can(Perm.STAFF_MANAGE)) {
            Ui.toast(this, "최고관리자만 쓸 수 있어요.");
            return;
        }
        int trash = 0;
        JSONArray ma = store.arr("merchants");
        for (int i = 0; i < ma.length(); i++) if (ma.optJSONObject(i).optBoolean("deleted")) trash++;
        List<File> autos = Backups.list(this, "auto_");
        String last = autos.isEmpty() ? "아직 없음" : backupLabel(autos.get(0)).replace("자동백업 · ", "");
        final String[] items = {"삭제한 가맹점 복원 (" + trash + "곳)", "백업·되돌리기 목록에서 복원", "백업 파일을 직접 골라 복원", "지금 바로 백업 만들기"};
        new AlertDialog.Builder(this).setTitle("가맹점 보호·복원\n(최근 자동백업 " + last + ")").setItems(items, (d, w) -> {
            if (w == 0) showMerchantTrash();
            else if (w == 1) showBackupList();
            else if (w == 2) {
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(Intent.createChooser(i, "백업 파일(.json) 고르기"), REQ_RESTORE);
                } catch (Exception e) {
                    Ui.toast(this, "열 수 있는 앱이 없어요.");
                }
            } else {
                File f = snapshotMerchants("수동백업");
                autoBackup();
                Ui.toast(this, f == null ? "백업을 만들지 못했어요." : "백업했어요. (되돌리기 목록에 있어요)");
            }
        }).setNegativeButton("닫기", null).show();
    }

    private void showMerchantTrash() {
        final List<JSONObject> del = new ArrayList<>();
        JSONArray a = store.arr("merchants");
        for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optBoolean("deleted")) del.add(a.optJSONObject(i));
        if (del.isEmpty()) {
            Ui.toast(this, "삭제된 가맹점이 없어요.");
            return;
        }
        Collections.sort(del, (x, y) -> x.optString("name").compareTo(y.optString("name")));
        String[] names = new String[del.size()];
        for (int i = 0; i < names.length; i++) names[i] = del.get(i).optString("name") + (del.get(i).optString("addr").isEmpty() ? "" : "  ·  " + del.get(i).optString("addr"));
        new AlertDialog.Builder(this).setTitle("삭제한 가맹점 (눌러서 복원)").setItems(names, (d, w) -> {
            final JSONObject m = del.get(w);
            new AlertDialog.Builder(this).setTitle("'" + m.optString("name") + "' 복원할까요?").setMessage(merchantInfo(m))
                    .setNegativeButton("취소", null).setPositiveButton("복원", (d2, w2) -> {
                        try {
                            m.put("deleted", false);
                        } catch (Exception ignored) {
                        }
                        store.save();
                        Ui.toast(this, "복원했어요.");
                        render();
                    }).show();
        }).setNegativeButton("닫기", null).show();
    }

    private String backupLabel(File f) {
        String n = f.getName().replace(".json", "");
        try {
            if (n.startsWith("snap_")) {
                String[] p = n.split("_", 4);
                return "되돌리기 기준점 · " + p[1].substring(4, 6) + "/" + p[1].substring(6, 8) + " " + p[2].substring(0, 2) + ":" + p[2].substring(2, 4) + " · " + (p.length > 3 ? p[3] : "");
            }
            String d = n.substring(5);
            return "자동백업 · " + d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
        } catch (Exception e) {
            return n;
        }
    }

    private void showBackupList() {
        final List<File> files = new ArrayList<>();
        files.addAll(Backups.list(this, "snap_"));
        files.addAll(Backups.list(this, "auto_"));
        if (files.isEmpty()) {
            Ui.toast(this, "아직 보관된 백업이 없어요. '지금 바로 백업 만들기'를 눌러 보세요.");
            return;
        }
        String[] labels = new String[files.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = backupLabel(files.get(i));
        new AlertDialog.Builder(this).setTitle("복원할 백업 고르기").setItems(labels, (d, w) -> {
            final File f = files.get(w);
            final String label = backupLabel(f);
            new Thread(() -> {
                try {
                    final JSONObject o = new JSONObject(Backups.read(f));
                    final JSONArray ms = o.optJSONArray("merchants");
                    if (ms == null) throw new Exception("no merchants");
                    runOnUiThread(() -> restorePreview(ms, o.optBoolean("exact"), label));
                } catch (Exception e) {
                    runOnUiThread(() -> Ui.toast(this, "백업 파일을 읽지 못했어요."));
                }
            }).start();
        }).setNegativeButton("닫기", null).show();
    }

    /** 백업 내용과 지금을 견주어 보여 주고, 확인하면 복원한다. 복원 직전 상태도 자동으로 저장해 다시 돌릴 수 있다. */
    private void restorePreview(final JSONArray backup, final boolean exact, String label) {
        java.util.Map<Integer, JSONObject> cur = new java.util.HashMap<>();
        JSONArray ca = store.arr("merchants");
        for (int i = 0; i < ca.length(); i++) cur.put(ca.optJSONObject(i).optInt("id"), ca.optJSONObject(i));
        int same = 0, diff = 0, missing = 0, extra = 0;
        java.util.HashSet<Integer> inB = new java.util.HashSet<>();
        for (int i = 0; i < backup.length(); i++) {
            JSONObject b = backup.optJSONObject(i);
            if (b == null) continue;
            inB.add(b.optInt("id"));
            JSONObject c = cur.get(b.optInt("id"));
            if (c == null) missing++;
            else if (Store.sameJson(c, b)) same++;
            else diff++;
        }
        if (exact) for (JSONObject c : cur.values()) if (!inB.contains(c.optInt("id")) && !c.optBoolean("deleted")) extra++;
        if (diff + missing + extra == 0) {
            Ui.toast(this, "지금 상태와 같아서 바꿀 게 없어요.");
            return;
        }
        String msg = label + "\n백업에 든 가맹점 " + backup.length() + "곳\n\n• 지금과 같음 " + same + "곳\n• 내용이 달라서 백업 내용으로 바꿈 " + diff + "곳\n• 지금 없는데 되살림 " + missing + "곳"
                + (exact ? "\n• 그 뒤 새로 생겨서 목록에서 숨김 " + extra + "곳" : "")
                + "\n\n복원 직전 상태는 자동으로 저장돼서, 마음에 안 들면 다시 되돌릴 수 있어요.";
        new AlertDialog.Builder(this).setTitle("복원 미리보기").setMessage(msg).setNegativeButton("취소", null)
                .setPositiveButton("복원", (d, w) -> {
                    if (snapshotMerchants("복원전") == null) {
                        Ui.toast(this, "복원 전 백업을 만들지 못해서 멈췄어요. 저장 공간을 확인해 주세요.");
                        return;
                    }
                    int[] r = store.restoreMerchants(backup, exact);
                    Ui.toast(this, "복원했어요. 바뀜 " + r[0] + " · 되살림 " + r[1] + (exact ? " · 숨김 " + r[2] : ""));
                    render();
                }).show();
    }

    private static final String[] MFIELDS = {"name", "상호", "bizno", "사업자번호", "ecode", "거래처코드", "owner", "대표자", "phone", "전화", "mobile", "휴대폰", "addr", "주소", "grade", "등급", "memo", "메모", "deleted", "삭제"};

    private static String diffLabel(JSONObject prev, JSONObject cur) {
        if (prev == null) return "처음 등록";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MFIELDS.length; i += 2) {
            String a = prev.has(MFIELDS[i]) ? String.valueOf(prev.opt(MFIELDS[i])) : "";
            String b = cur.has(MFIELDS[i]) ? String.valueOf(cur.opt(MFIELDS[i])) : "";
            if (!a.equals(b)) sb.append(sb.length() == 0 ? "" : ", ").append(MFIELDS[i + 1]);
        }
        return sb.length() == 0 ? "기타 변경" : sb + " 변경";
    }

    /** 서버에 남은 변경 기록(audit)으로 이 가맹점의 예전 모습을 보고 그 시점으로 되돌린다. */
    private void showMerchantHistory(final JSONObject m) {
        Ui.toast(this, "변경 이력을 불러오는 중…");
        Cloud.fs().collection("audit").whereEqualTo("c", "merchants").whereEqualTo("id", (long) m.optInt("id")).get().addOnSuccessListener(qs -> {
            final List<Object[]> vs = new ArrayList<>();
            for (com.google.firebase.firestore.DocumentSnapshot d : qs.getDocuments()) {
                try {
                    Long u = d.getLong("u");
                    Long by = d.getLong("by");
                    vs.add(new Object[]{u == null ? 0L : u, by == null ? 0 : (int) (long) by, new JSONObject(String.valueOf(d.getString("j")))});
                } catch (Exception ignored) {
                }
            }
            if (vs.isEmpty()) {
                Ui.toast(this, "남아 있는 변경 이력이 없어요.");
                return;
            }
            Collections.sort(vs, (x, y) -> Long.compare((Long) x[0], (Long) y[0]));
            final String[] labels = new String[vs.size()];
            SimpleDateFormat df = new SimpleDateFormat("M/d HH:mm", Locale.KOREA);
            for (int i = 0; i < vs.size(); i++) {
                Object[] v = vs.get(i);
                labels[vs.size() - 1 - i] = df.format(new Date((Long) v[0])) + " · " + store.userName((Integer) v[1]) + " · " + diffLabel(i == 0 ? null : (JSONObject) vs.get(i - 1)[2], (JSONObject) v[2]);
            }
            final List<Object[]> newestFirst = new ArrayList<>(vs);
            Collections.reverse(newestFirst);
            new AlertDialog.Builder(this).setTitle("'" + m.optString("name") + "' 변경 이력 (눌러서 그 시점으로)").setItems(labels, (d, w) -> {
                final JSONObject ver = (JSONObject) newestFirst.get(w)[2];
                new AlertDialog.Builder(this).setTitle("이 시점으로 되돌릴까요?").setMessage(merchantInfo(ver) + (ver.optBoolean("deleted") ? "\n\n(이 시점엔 삭제된 상태였어요)" : ""))
                        .setNegativeButton("취소", null).setPositiveButton("되돌리기", (d2, w2) -> {
                            if (snapshotMerchants("이력되돌리기전") == null) {
                                Ui.toast(this, "되돌리기 전 백업을 만들지 못해서 멈췄어요.");
                                return;
                            }
                            store.restoreMerchants(new JSONArray().put(ver), false);
                            Ui.toast(this, "되돌렸어요.");
                            render();
                        }).show();
            }).setNegativeButton("닫기", null).show();
        }).addOnFailureListener(e -> Ui.toast(this, "이력을 불러오지 못했어요. (" + e.getMessage() + ")"));
    }

    // ---------- 1:1 비공개 대화 ----------
    private String roomTitle(JSONObject r) {
        if (!r.optBoolean("dm")) return r.optString("name");
        JSONArray mm = r.optJSONArray("members");
        int other = 0;
        if (mm != null) for (int i = 0; i < mm.length(); i++) if (mm.optInt(i) != myId()) other = mm.optInt(i);
        return other == 0 ? "나와의 대화" : store.userName(other);
    }

    private void showStartDm() {
        final List<JSONObject> us = new ArrayList<>();
        JSONArray ua = store.users();
        for (int i = 0; i < ua.length(); i++) {
            JSONObject u = ua.optJSONObject(i);
            if (u.optInt("id") != myId() && u.optBoolean("active", true)) us.add(u);
        }
        if (us.isEmpty()) {
            Ui.toast(this, "대화할 직원이 아직 없어요.");
            return;
        }
        Collections.sort(us, (x, y) -> x.optString("name").compareTo(y.optString("name")));
        String[] names = new String[us.size()];
        for (int i = 0; i < names.length; i++) names[i] = us.get(i).optString("name");
        new AlertDialog.Builder(this).setTitle("누구와 1:1 대화할까요?").setItems(names, (d, w) -> startDm(us.get(w).optInt("id"))).setNegativeButton("취소", null).show();
    }

    private void startDm(int uid) {
        JSONObject other = store.user(uid);
        if (other == null) return;
        com.google.firebase.auth.FirebaseUser au = Cloud.auth().getCurrentUser();
        String pa = au != null && au.getPhoneNumber() != null ? au.getPhoneNumber() : (me == null ? "" : me.optString("phone"));
        String pb = other.optString("phone");
        if (pa.isEmpty() || pb.isEmpty()) {
            Ui.toast(this, "두 사람 모두 전화번호가 등록돼 있어야 해요.");
            return;
        }
        JSONObject r = store.dmRoom(myId(), pa, uid, pb);
        if (r == null) {
            Ui.toast(this, "대화방을 만들지 못했어요.");
            return;
        }
        chatRoom = r.optInt("id");
        draft = "";
        showRoom();
    }

    /** 반응 고르기: 하트·엄지 등 목록 + (내 반응이 있으면) 지우기. 텔레그램처럼. */
    private void showReactPicker(final JSONObject m) {
        showMsgMenu(m);
    }

    /** 길게 누르면(또는 붙은 이모티콘을 누르면): 맨 위 이모티콘 줄 + 답장 · 복사 · (내 글) 삭제. */
    private void showMsgMenu(final JSONObject m) {
        final boolean mine = m.optInt("who") == myId();
        final boolean hasText = m.optString("kind").isEmpty();
        final AlertDialog[] dd = new AlertDialog[1];
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 6));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        String myR = "";
        JSONObject rr = m.optJSONObject("react");
        if (rr != null) myR = rr.optString(String.valueOf(myId()));
        for (final String e : REACTS) {
            TextView t = Ui.text(this, e, 26, Ui.INK, false);
            t.setGravity(Gravity.CENTER);
            if (e.equals(myR)) t.setBackground(Ui.round(Ui.TINT, 12, this));
            t.setOnClickListener(v -> {
                dd[0].dismiss();
                toggleReaction(m, e);
            });
            row.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f));
        }
        col.addView(row);
        final List<String> items = new ArrayList<>();
        items.add("답장");
        if (hasText) items.add("복사");
        if (mine) items.add("삭제");
        for (final String it : items) {
            TextView b = Ui.text(this, it, 16, "삭제".equals(it) ? Color.parseColor("#C0392B") : Ui.INK, false);
            b.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            b.setOnClickListener(v -> {
                dd[0].dismiss();
                if ("답장".equals(it)) setReply(m);
                else if ("복사".equals(it)) {
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(android.content.ClipData.newPlainText("메시지", m.optString("text")));
                    Ui.toast(this, "복사했어요.");
                } else {
                    new AlertDialog.Builder(this).setTitle("내 메시지를 삭제할까요?").setMessage("대화에서 사라지고, 서버에는 기록이 보관돼요.")
                            .setNegativeButton("취소", null).setPositiveButton("삭제", (d2, w2) -> {
                                try {
                                    m.put("deleted", true);
                                } catch (Exception ignored) {
                                }
                                store.save();
                                fillMsgs();
                            }).show();
                }
            });
            col.addView(b);
        }
        dd[0] = new AlertDialog.Builder(this).setView(col).show();
    }

    /** 한 번 탭 = onTap, 두 번 탭 = ❤️ 좋아요, 길게 = 메뉴. */
    private void attachGestures(View v, final JSONObject m, final Runnable onTap) {
        final android.view.GestureDetector gd = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(android.view.MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                if (onTap != null) onTap.run();
                return true;
            }

            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                toggleReaction(m, "❤️");
                return true;
            }

            @Override
            public void onLongPress(android.view.MotionEvent e) {
                showMsgMenu(m);
            }
        });
        v.setOnTouchListener((x, ev) -> gd.onTouchEvent(ev));
    }

    private View quoteView(JSONObject rto, boolean onGold) {
        LinearLayout q = new LinearLayout(this);
        q.setOrientation(LinearLayout.VERTICAL);
        q.setBackground(Ui.round(onGold ? Color.argb(60, 255, 255, 255) : Ui.FIELD, 8, this));
        q.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        q.addView(Ui.text(this, "↪ " + store.userName(rto.optInt("who")), 11, Ui.DEEP, true));
        TextView t = Ui.text(this, rto.optString("text"), 12, Ui.GRAY, false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        q.addView(t);
        return q;
    }

    private View buildBubble(final JSONObject m, final boolean mine) {
        final String kind = m.optString("kind");
        final int chunks = m.optInt("chunks", 0);
        int maxW = Math.min(Ui.dp(this, 250), (int) (getResources().getDisplayMetrics().widthPixels * 0.62));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(mine ? Gravity.END : Gravity.START);
        JSONObject rto = m.optJSONObject("rto");
        View core;
        if ("img".equals(kind)) {
            if (rto != null) wrap.addView(quoteView(rto, false), new LinearLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT));
            final ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setMaxWidth(Ui.dp(this, 220));
            iv.setMaxHeight(Ui.dp(this, 300));
            iv.setBackground(Ui.round(Ui.FIELD, 14, this));
            iv.setMinimumWidth(Ui.dp(this, 120));
            iv.setMinimumHeight(Ui.dp(this, 90));
            iv.setClipToOutline(true);
            final String id = m.optString("blob");
            final File f = Media.blobFile(this, id);
            if (f.exists() && f.length() > 0) {
                android.graphics.Bitmap b = thumb(f, 600);
                if (b != null) iv.setImageBitmap(b);
            } else {
                fetchBlob(id, chunks, () -> {
                    android.graphics.Bitmap b = thumb(f, 600);
                    if (b != null) iv.setImageBitmap(b);
                });
            }
            attachGestures(iv, m, () -> showImage(id, chunks, m.optString("fname")));
            core = iv;
            wrap.addView(core, Ui.lp(this, rto != null ? 3 : 0));
        } else if ("stk".equals(kind)) {
            if (rto != null) wrap.addView(quoteView(rto, false), new LinearLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT));
            ImageView iv = new ImageView(this);
            iv.setImageResource(stkRes(m.optString("blob", "1")));
            iv.setAdjustViewBounds(true);
            iv.setMaxWidth(Ui.dp(this, 150));
            iv.setMaxHeight(Ui.dp(this, 158));
            attachGestures(iv, m, null);
            wrap.addView(iv, Ui.lp(this, rto != null ? 3 : 0));
        } else {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(Ui.round(mine ? Ui.GOLD : Color.WHITE, 16, this));
            box.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            if (rto != null) {
                box.addView(quoteView(rto, mine), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            TextView b;
            Runnable tap = null;
            if ("file".equals(kind)) {
                b = Ui.text(this, "📎 " + m.optString("fname") + "\n" + sizeText(m.optLong("size")) + " · 눌러서 열기", 14, Ui.INK, false);
                tap = () -> openBlob(m.optString("blob"), chunks, m.optString("fname"), false);
            } else {
                b = Ui.text(this, m.optString("text"), 15, Ui.INK, false);
                final java.util.List<String> urls = new java.util.ArrayList<>();
                java.util.regex.Matcher um = android.util.Patterns.WEB_URL.matcher(m.optString("text"));
                while (um.find()) urls.add(um.group());
                if (!urls.isEmpty()) {
                    android.text.util.Linkify.addLinks(b, android.util.Patterns.WEB_URL, null);
                    b.setMovementMethod(null); // 두 번 탭·길게 누르기가 먹도록, 열기는 한 번 탭으로 직접 처리
                    tap = () -> openLinks(urls);
                }
            }
            b.setMaxWidth(maxW);
            b.setLineSpacing(0, 1.1f);
            box.addView(b, Ui.lp(this, rto != null ? 6 : 0));
            attachGestures(box, m, tap);
            wrap.addView(box);
        }
        // 반응(이모티콘) 줄
        JSONObject re = m.optJSONObject("react");
        if (re != null && re.length() > 0) {
            java.util.Map<String, Integer> cnt = new java.util.LinkedHashMap<>();
            boolean[] mineR = new boolean[1];
            java.util.Iterator<String> it = re.keys();
            String myKey = String.valueOf(myId());
            while (it.hasNext()) {
                String k = it.next();
                String e = re.optString(k);
                if (e.isEmpty()) continue;
                Integer c = cnt.get(e);
                cnt.put(e, c == null ? 1 : c + 1);
            }
            LinearLayout rr = new LinearLayout(this);
            for (final java.util.Map.Entry<String, Integer> en : cnt.entrySet()) {
                boolean my = en.getKey().equals(re.optString(myKey));
                TextView c = Ui.chip(this, en.getKey() + " " + en.getValue(), my ? Ui.TINT : Color.WHITE, my ? Ui.DEEP : Ui.GRAY);
                c.setOnClickListener(v -> showReactPicker(m));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cp.rightMargin = Ui.dp(this, 4);
                rr.addView(c, cp);
            }
            wrap.addView(rr, Ui.lp(this, 3));
        }
        return wrap;
    }

    // ---------- 프로필 ----------
    private static final int[] AV_BG = {Color.rgb(247, 239, 217), Color.rgb(227, 246, 238), Color.rgb(241, 236, 251), Color.rgb(255, 241, 219), Color.rgb(232, 240, 253), Color.rgb(253, 236, 234)};

    /** 동그란 프로필 그림. 사진이 있으면 사진, 없으면 이름 첫 글자. */
    private View avatarView(int uid, int sizeDp) {
        JSONObject u = store.user(uid);
        String name = u == null ? "?" : u.optString("name");
        JSONObject p = store.profile(uid);
        String pic = p == null ? "" : p.optString("pic");
        if (!pic.isEmpty()) {
            String key = "pic" + uid + "@" + pic.length();
            android.graphics.Bitmap b = bmpCache.get(key);
            if (b == null) {
                try {
                    byte[] d = Media.unb64(pic);
                    b = android.graphics.BitmapFactory.decodeByteArray(d, 0, d.length);
                    if (b != null) bmpCache.put(key, b);
                } catch (Exception ignored) {
                }
            }
            if (b != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(b);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setClipToOutline(true);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View v, android.graphics.Outline o) {
                        o.setOval(0, 0, v.getWidth(), v.getHeight());
                    }
                });
                iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
                return iv;
            }
        }
        boolean me2 = uid == myId();
        return Ui.circle(this, name, sizeDp, me2 ? Ui.GOLD : AV_BG[Math.abs(uid) % AV_BG.length], me2 ? Ui.DARK : Ui.DEEP);
    }

    private void showProfile(final int uid) {
        JSONObject u = store.user(uid);
        if (u == null) return;
        JSONObject p = store.profile(uid);
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setGravity(Gravity.CENTER_HORIZONTAL);
        f.setPadding(Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 8));
        f.addView(avatarView(uid, 96));
        f.addView(Ui.text(this, u.optString("name"), 22, Ui.INK, true), Ui.lp(this, 12));
        f.addView(Ui.text(this, Store.has(u, Perm.STAFF_MANAGE) ? "관리자" : "직원", 13, Ui.GRAY, false), Ui.lp(this, 2));
        String st = p == null ? "" : p.optString("status");
        TextView stv = Ui.text(this, st.isEmpty() ? "상태 메시지가 없어요" : st, 15, st.isEmpty() ? Ui.GRAY : Ui.INK, false);
        stv.setGravity(Gravity.CENTER);
        f.addView(stv, Ui.lp(this, 10));
        if (!u.optString("phone").isEmpty()) {
            TextView ph = Ui.text(this, "📞 " + u.optString("phone"), 15, Ui.DEEP, true);
            ph.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + u.optString("phone"))));
                } catch (Exception ignored) {
                }
            });
            ph.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
            f.addView(ph);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this).setView(f).setNegativeButton("닫기", null);
        if (uid == myId()) {
            b.setNeutralButton("사진 변경", (d, w) -> {
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    startActivityForResult(Intent.createChooser(i, "프로필 사진 고르기"), REQ_PROFILE);
                } catch (Exception e) {
                    Ui.toast(this, "열 수 있는 앱이 없어요.");
                }
            });
            b.setPositiveButton("상태 메시지", (d, w) -> {
                LinearLayout g = form();
                final EditText e = Ui.field(this, "상태 메시지 (예: 외근 중, 오후 3시 복귀)", false);
                JSONObject pp = store.profile(uid);
                e.setText(pp == null ? "" : pp.optString("status"));
                g.addView(e);
                new AlertDialog.Builder(this).setTitle("상태 메시지").setView(g).setNegativeButton("취소", null)
                        .setPositiveButton("저장", (d2, w2) -> {
                            try {
                                store.myProfile(uid).put("status", e.getText().toString().trim());
                            } catch (Exception ignored) {
                            }
                            store.save();
                            Ui.toast(this, "저장했어요.");
                        }).show();
            });
        } else {
            b.setNeutralButton("1:1 대화", (d, w) -> startDm(uid));
            b.setPositiveButton("전화하기", (d, w) -> {
                if (u.optString("phone").isEmpty()) return;
                try {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + u.optString("phone"))));
                } catch (Exception ignored) {
                }
            });
        }
        b.show();
    }

    // ======================= v0.7: 바코드 스캔 · 시리얼 촬영 인식 =======================
    private EditText ocrTarget;
    private boolean ocrAppend;
    private File ocrFile;

    private static void putLine(EditText t, String v, boolean append) {
        String cur = t.getText().toString();
        if (!append || cur.trim().isEmpty()) {
            t.setText(v);
        } else {
            for (String l : cur.split("\\n")) if (l.trim().equals(v)) return; // 같은 줄이 있으면 중복 방지
            t.setText(cur.replaceAll("\\s+$", "") + "\n" + v);
        }
        t.setSelection(t.getText().length());
    }

    /** 구글 코드 스캐너(카메라 앱)로 바코드를 딱 한 번 읽는다. 더 읽으려면 다시 누른다. */
    private void scanOnce(final java.util.function.Consumer<String> onCode) {
        com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions opts = new com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                .enableAutoZoom().build();
        com.google.mlkit.vision.codescanner.GmsBarcodeScanner sc = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, opts);
        sc.startScan().addOnSuccessListener(b -> {
            String v = b.getRawValue();
            if (v == null || v.trim().isEmpty()) Ui.toast(this, "바코드 값을 읽지 못했어요.");
            else onCode.accept(v.trim());
        }).addOnFailureListener(e -> Ui.toast(this, "바코드 스캔을 시작하지 못했어요. (" + e.getMessage() + ")"));
    }

    private void scanBarcode(final EditText target, final boolean append) {
        scanOnce(v -> {
            putLine(target, v, append);
            Ui.toast(this, "읽었어요: " + v);
        });
    }

    /** 시리얼 라벨을 카메라로 찍어 글자를 읽는다. */
    private void ocrSerial(EditText target, boolean append) {
        ocrTarget = target;
        ocrAppend = append;
        ocrFile = new File(Media.dir(this), "ocr.jpg");
        try {
            startActivityForResult(cameraIntent(ocrFile), REQ_OCR);
        } catch (Exception e) {
            Ui.toast(this, "카메라 앱을 열 수 없어요.");
        }
    }

    private void runOcr() {
        if (ocrTarget == null || ocrFile == null || !ocrFile.exists()) return;
        android.graphics.Bitmap bmp = Media.decode(getContentResolver(), Uri.fromFile(ocrFile), 2400);
        if (bmp == null) {
            Ui.toast(this, "사진을 읽지 못했어요.");
            return;
        }
        Ui.toast(this, "글자를 읽는 중…");
        com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener(t -> handleOcr(t.getText()))
                .addOnFailureListener(e -> Ui.toast(this, "글자를 읽지 못했어요. (" + e.getMessage() + ")"));
    }

    /** 읽은 글자 중 시리얼처럼 생긴 것(영문·숫자, 6~30자, 숫자 포함)만 추려서 고르게 한다. */
    private void handleOcr(String text) {
        List<String> cand = new ArrayList<>();
        for (String raw : text.split("[\\s:：,;|]+")) {
            String tk = raw.replaceAll("^(?i)(S/N|SN|SERIAL|NO\\.?)[.:#-]*", "").replaceAll("[^0-9A-Za-z-]", "");
            if (tk.length() >= 6 && tk.length() <= 30 && tk.matches(".*[0-9].*") && !cand.contains(tk)) cand.add(tk);
        }
        if (cand.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("시리얼을 찾지 못했어요")
                    .setMessage("글자가 또렷하게 나오도록 가까이서, 흔들리지 않게 다시 찍어 보세요. 바코드가 있으면 '바코드 스캔'이 더 정확해요.")
                    .setPositiveButton("확인", null).show();
            return;
        }
        final String[] arr = cand.toArray(new String[0]);
        final boolean[] on = new boolean[arr.length];
        if (arr.length == 1) on[0] = true;
        new AlertDialog.Builder(this).setTitle("읽은 시리얼 (맞는 것을 고르세요)")
                .setMultiChoiceItems(arr, on, (d, i, c) -> on[i] = c)
                .setNegativeButton("취소", null)
                .setPositiveButton("넣기", (d, w) -> {
                    boolean first = true;
                    for (int i = 0; i < arr.length; i++) {
                        if (!on[i]) continue;
                        putLine(ocrTarget, arr[i], ocrAppend || !first);
                        first = false;
                    }
                }).show();
    }

    /** 칸 아래에 붙이는 [바코드 스캔][시리얼 촬영] 버튼 줄. */
    private LinearLayout scanButtons(final EditText target, final boolean append) {
        LinearLayout r = new LinearLayout(this);
        Button a = Ui.button(this, "바코드 스캔", Ui.TINT, Ui.DEEP);
        a.setOnClickListener(v -> scanBarcode(target, append));
        Button b = Ui.button(this, "시리얼 촬영", Ui.TINT, Ui.DEEP);
        b.setOnClickListener(v -> ocrSerial(target, append));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.rightMargin = Ui.dp(this, 4);
        LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        q.leftMargin = Ui.dp(this, 4);
        r.addView(a, p);
        r.addView(b, q);
        LinearLayout.LayoutParams rp = Ui.lp(this, 6);
        r.setLayoutParams(rp);
        return r;
    }

    // ======================= v0.7: 단가·수익, 월별 비교 =======================
    private static long parseWon(EditText e) {
        try {
            return Long.parseLong(e.getText().toString().replaceAll("[^0-9]", ""));
        } catch (Exception x) {
            return 0;
        }
    }

    private static String monthKey(int y, int m, int delta) {
        int t = y * 12 + (m - 1) + delta;
        return String.format(Locale.KOREA, "%04d-%02d", t / 12, t % 12 + 1);
    }

    /** 수익 계산에 쓰는 한 줄: 출고된 장비 하나의 {월, 담당, 입고가, 출고가, 재고줄}. */
    private static final class Sale {
        String month;
        int who;
        long buy, sell;
        JSONObject row;
        boolean priced;
    }

    private List<Sale> sales() {
        List<Sale> out = new ArrayList<>();
        for (JSONObject r : store.list(store.arr("stock"))) {
            if (r.optBoolean("deleted")) continue;
            String st = r.optString("status");
            if (!"출고중".equals(st) && !"설치완료".equals(st)) continue;
            JSONObject c = store.byId("contracts", r.optInt("contract"));
            if (c == null) continue;
            Sale s = new Sale();
            s.month = c.optString("date").length() >= 7 ? c.optString("date").substring(0, 7) : "";
            s.who = c.optInt("who");
            s.row = r;
            JSONObject cd = store.costDoc(r.optInt("id"), false);
            s.buy = cd == null ? 0 : cd.optLong("buy");
            s.sell = cd == null ? 0 : cd.optLong("sell");
            s.priced = cd != null && (s.buy > 0 || s.sell > 0);
            out.add(s);
        }
        return out;
    }

    private void renderProfit() {
        Calendar now = Calendar.getInstance();
        if (perfY == 0) {
            perfY = now.get(Calendar.YEAR);
            perfM = now.get(Calendar.MONTH) + 1;
        }
        content.addView(title("수익 현황"));
        content.addView(Ui.text(this, "재고에 적어 둔 입고 단가(산 값)와 출고 단가(판 값)의 차이로 계산해요. 계약에 올라가 출고된 장비가 대상입니다.", 13, Ui.GRAY, false));
        monthNav(() -> render());
        String key = String.format(Locale.KOREA, "%04d-%02d", perfY, perfM);
        List<Sale> all = sales();
        long totSell = 0, totBuy = 0, mSell = 0, mBuy = 0;
        int mCnt = 0, missing = 0;
        java.util.Map<Integer, long[]> byWho = new java.util.HashMap<>();
        for (Sale s : all) {
            if (!s.priced) {
                missing++;
                continue;
            }
            totSell += s.sell;
            totBuy += s.buy;
            if (s.month.equals(key)) {
                mSell += s.sell;
                mBuy += s.buy;
                mCnt++;
                long[] a = byWho.get(s.who);
                if (a == null) {
                    a = new long[2];
                    byWho.put(s.who, a);
                }
                a[0]++;
                a[1] += s.sell - s.buy;
            }
        }
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(Ui.round(Ui.GOLD, 24, this));
        hero.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        hero.addView(Ui.text(this, "지금까지 총 수익", 14, Color.argb(200, 31, 27, 18), false));
        hero.addView(Ui.text(this, won(totSell - totBuy), 30, Ui.DARK, true));
        hero.addView(Ui.text(this, "매출 " + won(totSell) + " − 매입 " + won(totBuy), 13, Color.argb(200, 31, 27, 18), false), Ui.lp(this, 4));
        content.addView(hero, Ui.lp(this, 14));

        LinearLayout mc = Ui.card(this);
        mc.addView(Ui.text(this, perfM + "월 수익", 14, Ui.GRAY, false));
        mc.addView(Ui.text(this, won(mSell - mBuy), 24, mSell - mBuy < 0 ? Ui.RED : Ui.INK, true));
        mc.addView(Ui.text(this, mCnt + "대 · 매출 " + won(mSell) + " · 매입 " + won(mBuy), 13, Ui.GRAY, false), Ui.lp(this, 2));
        content.addView(mc, Ui.lp(this, 10));

        // 최근 6개월 수익 그래프
        content.addView(Ui.text(this, "최근 6개월 수익", 18, Ui.INK, true), Ui.lp(this, 24));
        long[] pr = new long[6];
        String[] lb = new String[6];
        long max = 1;
        for (int i = 0; i < 6; i++) {
            String k = monthKey(perfY, perfM, i - 5);
            lb[i] = Integer.parseInt(k.substring(5)) + "월";
            for (Sale s : all) if (s.priced && s.month.equals(k)) pr[i] += s.sell - s.buy;
            max = Math.max(max, Math.abs(pr[i]));
        }
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.text(this, lb[i], 13, Ui.GRAY, false), new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(bar((int) (Math.abs(pr[i]) * 100 / max), pr[i] < 0 ? Ui.RED : (i == 5 ? Ui.GOLD : Ui.DEEP), Ui.FIELD, 14), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView v = Ui.text(this, won(pr[i]), 12, Ui.INK, false);
            v.setGravity(Gravity.END);
            row.addView(v, new LinearLayout.LayoutParams(Ui.dp(this, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(row, Ui.lp(this, 8));
        }
        // 직원별(선택한 달)
        content.addView(Ui.text(this, perfM + "월 직원별 수익", 18, Ui.INK, true), Ui.lp(this, 24));
        if (byWho.isEmpty()) content.addView(Ui.text(this, "이 달에 단가가 입력된 출고 장비가 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 8));
        for (java.util.Map.Entry<Integer, long[]> e : byWho.entrySet()) {
            LinearLayout row = Ui.cardH(this);
            row.addView(Ui.text(this, store.userName(e.getKey()), 15, Ui.INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.text(this, e.getValue()[0] + "대 · " + won(e.getValue()[1]), 14, e.getValue()[1] < 0 ? Ui.RED : Ui.DEEP, true));
            content.addView(row, Ui.lp(this, 8));
        }
        // 단가 미입력
        if (missing > 0) {
            content.addView(Ui.text(this, "단가를 아직 안 적은 출고 장비 " + missing + "대", 18, Ui.RED, true), Ui.lp(this, 24));
            content.addView(Ui.text(this, "눌러서 입고·출고 단가를 입력하면 수익에 반영돼요.", 12, Ui.GRAY, false));
            int n = 0;
            for (final Sale s : all) {
                if (s.priced) continue;
                if (++n > 30) break;
                LinearLayout row = Ui.cardH(this);
                row.addView(Ui.text(this, s.row.optString("model") + (s.row.optString("serial").isEmpty() ? "" : "  ·  " + s.row.optString("serial")), 14, Ui.INK, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(Ui.text(this, s.month, 12, Ui.GRAY, false));
                row.setOnClickListener(v -> showStockRow(s.row));
                content.addView(row, Ui.lp(this, 6));
            }
        }
    }

    /** 월별 비교: 지난달 → 이번달 건수와 성장률, 최근 6개월 추이. */
    private void renderTrend(String key, boolean mine) {
        int y = Integer.parseInt(key.substring(0, 4)), m = Integer.parseInt(key.substring(5));
        String prevKey = monthKey(y, m, -1);
        int pm = Integer.parseInt(prevKey.substring(5));
        java.util.Map<Integer, long[]> cur = perfStats(key, mine), pre = perfStats(prevKey, mine);
        content.addView(Ui.text(this, "월별 성과  (" + pm + "월 → " + m + "월)", 18, Ui.INK, true), Ui.lp(this, 24));
        long ct = 0, pt = 0;
        for (long[] a : cur.values()) ct += a[0];
        for (long[] a : pre.values()) pt += a[0];
        LinearLayout tot = Ui.card(this);
        tot.addView(Ui.text(this, mine ? "내 신규 계약" : "전체 신규 계약", 13, Ui.GRAY, false));
        TextView g = Ui.text(this, pt + "대 → " + ct + "대   " + growth(ct, pt), 20, growthColor(ct, pt), true);
        tot.addView(g, Ui.lp(this, 2));
        content.addView(tot, Ui.lp(this, 8));
        if (!mine) {
            long max = 1;
            for (long[] a : cur.values()) max = Math.max(max, a[0]);
            for (long[] a : pre.values()) max = Math.max(max, a[0]);
            for (JSONObject u : rankedStaff(cur)) {
                long[] a = cur.get(u.optInt("id")), b = pre.get(u.optInt("id"));
                long cn = a == null ? 0 : a[0], pn = b == null ? 0 : b[0];
                if (cn == 0 && pn == 0) continue;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(Ui.text(this, u.optString("name"), 14, Ui.INK, true), new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout bars = new LinearLayout(this);
                bars.setOrientation(LinearLayout.VERTICAL);
                bars.addView(bar((int) (pn * 100 / max), Color.rgb(200, 204, 214), Ui.FIELD, 8));
                bars.addView(bar((int) (cn * 100 / max), Ui.GOLD, Ui.FIELD, 8), Ui.lp(this, 3));
                row.addView(bars, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                LinearLayout rt = new LinearLayout(this);
                rt.setOrientation(LinearLayout.VERTICAL);
                rt.setGravity(Gravity.END);
                rt.addView(Ui.text(this, pn + " → " + cn, 12, Ui.GRAY, false));
                rt.addView(Ui.text(this, growth(cn, pn), 13, growthColor(cn, pn), true));
                row.addView(rt, new LinearLayout.LayoutParams(Ui.dp(this, 84), ViewGroup.LayoutParams.WRAP_CONTENT));
                content.addView(row, Ui.lp(this, 10));
            }
            content.addView(Ui.text(this, "회색 막대 = 지난달, 금색 막대 = 선택한 달", 11, Ui.GRAY, false), Ui.lp(this, 6));
        }
        content.addView(Ui.text(this, "최근 6개월 신규 계약", 16, Ui.INK, true), Ui.lp(this, 18));
        long[] n6 = new long[6];
        long mx = 1;
        for (int i = 0; i < 6; i++) {
            for (long[] a : perfStats(monthKey(y, m, i - 5), mine).values()) n6[i] += a[0];
            mx = Math.max(mx, n6[i]);
        }
        for (int i = 0; i < 6; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int mm = Integer.parseInt(monthKey(y, m, i - 5).substring(5));
            row.addView(Ui.text(this, mm + "월", 13, Ui.GRAY, false), new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(bar((int) (n6[i] * 100 / mx), i == 5 ? Ui.GOLD : Ui.DEEP, Ui.FIELD, 14), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView v = Ui.text(this, n6[i] + "대", 12, Ui.INK, false);
            v.setGravity(Gravity.END);
            row.addView(v, new LinearLayout.LayoutParams(Ui.dp(this, 48), ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(row, Ui.lp(this, 8));
        }
    }

    private static String growth(long cur, long prev) {
        if (prev == 0) return cur > 0 ? "신규" : "-";
        long p = Math.round((cur - prev) * 100.0 / prev);
        return (p > 0 ? "▲ +" : p < 0 ? "▼ " : "") + p + "%";
    }

    private static int growthColor(long cur, long prev) {
        if (cur > prev) return Ui.GREEN;
        if (cur < prev) return Ui.RED;
        return Ui.GRAY;
    }


    // ======================= v0.8: 가맹점 단체 문자 (폰 문자 앱으로 한 곳씩) =======================
    private final java.util.Set<Integer> smsSel = new java.util.HashSet<>();
    private String smsText = "";
    private String smsQuery = "";
    private List<JSONObject> smsQueue = null;
    private int smsPos = 0;

    private static String mobileOf(JSONObject m) {
        String d = m.optString("phone").replaceAll("[^0-9]", "");
        if (d.startsWith("01") && d.length() >= 10 && d.length() <= 11) return d;
        d = m.optString("mobile").replaceAll("[^0-9]", "");
        return d.startsWith("01") && d.length() >= 10 && d.length() <= 11 ? d : "";
    }

    private void renderSms() {
        if (smsQueue != null) {
            renderSmsSend();
            return;
        }
        content.addView(title("단체 문자 보내기"));
        content.addView(Ui.text(this, "고른 가맹점에 한 곳씩 이 폰의 문자 앱을 열어 드려요. 받는 분들끼리 번호가 보이지 않아요. 문자 요금은 이 폰의 요금제로 나갑니다. 내용에 {상호} 라고 쓰면 가맹점 이름으로 바뀌어요.", 13, Ui.GRAY, false));
        final EditText msg = Ui.field(this, "보낼 내용 (예: {상호} 사장님, 안녕하세요. 나인페이먼츠입니다.)", false);
        msg.setSingleLine(false);
        msg.setMinLines(3);
        msg.setText(smsText);
        content.addView(msg, Ui.lp(this, 10));
        final TextView cnt = Ui.text(this, "", 12, Ui.GRAY, false);
        content.addView(cnt, Ui.lp(this, 4));
        msg.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                smsText = s.toString();
                int by = 0;
                for (char ch : smsText.toCharArray()) by += ch > 127 ? 2 : 1;
                cnt.setText(smsText.length() + "자 · " + by + "바이트 " + (by > 90 ? "(90바이트를 넘으면 장문 문자로 나가요)" : ""));
            }
        });
        msg.setText(smsText);

        content.addView(Ui.text(this, "받는 가맹점", 18, Ui.INK, true), Ui.lp(this, 18));
        final TextView sel = Ui.text(this, "", 13, Ui.DEEP, true);
        content.addView(sel, Ui.lp(this, 2));
        // 등급으로 한 번에 고르기
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        hs.addView(chips);
        content.addView(hs, Ui.lp(this, 8));
        final EditText q = Ui.field(this, "상호·번호 검색 (초성도 돼요)", false);
        q.setText(smsQuery);
        content.addView(q, Ui.lp(this, 8));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        final Button go = Ui.button(this, "문자 보내기 시작", Ui.GOLD, Ui.DARK);
        final Runnable[] fillRef = new Runnable[1];
        final Runnable fill = () -> {
            list.removeAllViews();
            int n = 0;
            for (final JSONObject m : sortedMerchants()) {
                if (!Kor.match(m.optString("name") + " " + m.optString("phone"), smsQuery)) continue;
                final String mob = mobileOf(m);
                CheckBox cb = new CheckBox(this);
                cb.setText(m.optString("name") + "  ·  " + (mob.isEmpty() ? "문자 불가(휴대폰 번호 아님)" : m.optString("phone")) + "  [" + gradeLabel(m.optInt("grade", 5)) + "]");
                cb.setEnabled(!mob.isEmpty());
                cb.setChecked(smsSel.contains(m.optInt("id")));
                cb.setOnCheckedChangeListener((b, on) -> {
                    if (on) smsSel.add(m.optInt("id"));
                    else smsSel.remove(m.optInt("id"));
                    sel.setText("선택 " + smsSel.size() + "곳");
                    go.setText("문자 보내기 시작 (" + smsSel.size() + "곳)");
                });
                list.addView(cb);
                if (++n >= 300) break;
            }
            if (n == 0) list.addView(Ui.text(this, "가맹점이 없습니다.", 14, Ui.GRAY, false), Ui.lp(this, 12));
            sel.setText("선택 " + smsSel.size() + "곳");
            go.setText("문자 보내기 시작 (" + smsSel.size() + "곳)");
        };
        fillRef[0] = fill;
        TextView all = Ui.chip(this, "보이는 전체 선택", Ui.TINT, Ui.DEEP);
        all.setOnClickListener(v -> {
            for (JSONObject m : sortedMerchants()) {
                if (mobileOf(m).isEmpty() || !Kor.match(m.optString("name") + " " + m.optString("phone"), smsQuery)) continue;
                smsSel.add(m.optInt("id"));
            }
            render();
        });
        chips.addView(all);
        TextView none = Ui.chip(this, "선택 해제", Ui.FIELD, Ui.INK);
        none.setOnClickListener(v -> {
            smsSel.clear();
            render();
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Ui.dp(this, 6);
        chips.addView(none, cp);
        for (int g = 0; g < Store.GRADES.length; g++) {
            if (Store.GRADES[g].isEmpty()) continue;
            final int gg = g;
            TextView c = Ui.chip(this, Store.GRADES[g].split(" ")[0] + " 등급 선택", Ui.FIELD, Ui.INK);
            c.setOnClickListener(v -> {
                for (JSONObject m : sortedMerchants()) if (m.optInt("grade", 5) == gg && !mobileOf(m).isEmpty()) smsSel.add(m.optInt("id"));
                render();
            });
            chips.addView(c, cp);
        }
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                smsQuery = s.toString();
                fillRef[0].run();
            }
        });
        fill.run();
        go.setOnClickListener(v -> {
            if (smsText.trim().isEmpty()) {
                Ui.toast(this, "보낼 내용을 적어 주세요.");
                return;
            }
            List<JSONObject> qlist = new ArrayList<>();
            for (JSONObject m : sortedMerchants()) if (smsSel.contains(m.optInt("id")) && !mobileOf(m).isEmpty()) qlist.add(m);
            if (qlist.isEmpty()) {
                Ui.toast(this, "받는 가맹점을 골라 주세요.");
                return;
            }
            smsQueue = qlist;
            smsPos = 0;
            render();
        });
        content.addView(go, Ui.lp(this, 14));
    }

    private void renderSmsSend() {
        content.addView(title("문자 보내는 중"));
        if (smsPos >= smsQueue.size()) {
            content.addView(Ui.text(this, "모두 열어 드렸어요. (" + smsQueue.size() + "곳)", 16, Ui.INK, true), Ui.lp(this, 10));
            content.addView(Ui.text(this, "문자 앱에서 '전송'을 누르지 않은 곳은 보내지지 않았으니 문자 앱의 보낸 기록을 확인해 주세요.", 13, Ui.GRAY, false), Ui.lp(this, 6));
            Button done = Ui.button(this, "끝내기", Ui.GOLD, Ui.DARK);
            done.setOnClickListener(v -> {
                smsQueue = null;
                smsSel.clear();
                render();
            });
            content.addView(done, Ui.lp(this, 14));
            return;
        }
        final JSONObject m = smsQueue.get(smsPos);
        content.addView(Ui.text(this, (smsPos + 1) + " / " + smsQueue.size() + "곳", 14, Ui.DEEP, true), Ui.lp(this, 6));
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, m.optString("name"), 20, Ui.INK, true));
        c.addView(Ui.text(this, m.optString("phone"), 14, Ui.GRAY, false), Ui.lp(this, 2));
        final String body = smsText.replace("{상호}", m.optString("name"));
        TextView pv = Ui.text(this, body, 15, Ui.INK, false);
        pv.setLineSpacing(0, 1.25f);
        pv.setBackground(Ui.round(Color.WHITE, 14, this));
        pv.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        c.addView(pv, Ui.lp(this, 10));
        content.addView(c, Ui.lp(this, 10));
        Button open = Ui.button(this, "문자 앱 열기", Ui.GOLD, Ui.DARK);
        open.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + mobileOf(m)));
                i.putExtra("sms_body", body);
                startActivity(i);
                smsPos++;
                render();
            } catch (Exception e) {
                Ui.toast(this, "문자 앱을 열 수 없어요.");
            }
        });
        content.addView(open, Ui.lp(this, 14));
        content.addView(Ui.text(this, "문자 앱에서 내용을 확인하고 '전송'을 누른 뒤 이 앱으로 돌아오면 다음 가맹점이 준비돼 있어요.", 12, Ui.GRAY, false), Ui.lp(this, 8));
        Button skip = Ui.button(this, "이 가맹점 건너뛰기", Ui.FIELD, Ui.INK);
        skip.setOnClickListener(v -> {
            smsPos++;
            render();
        });
        content.addView(skip, Ui.lp(this, 10));
        Button stop = Ui.button(this, "중단하고 목록으로", Ui.FIELD, Ui.INK);
        stop.setOnClickListener(v -> {
            smsQueue = null;
            render();
        });
        content.addView(stop, Ui.lp(this, 8));
    }

    // ======================= v0.6: 일정 삭제·상세, 실적 순위, 일일보고, 실적 보고서 =======================
    private boolean canReport() {
        return can(Perm.REPORT_VIEW);
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
        renderTrend(key, mine);
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
