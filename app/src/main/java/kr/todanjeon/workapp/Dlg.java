package kr.todanjeon.workapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/**
 * 앱 전체 팝업의 공통 틀.
 * 1) 글자를 쓰는 칸이 있는 팝업은 바깥을 잘못 눌러도 닫히지 않는다(쓰던 내용 보호).
 * 2) 확인 버튼을 눌렀는데 빠진 항목이 있어 Ui.warn 이 불렸다면 팝업을 닫지 않고 그대로 둔다.
 */
final class Dlg extends AlertDialog.Builder {
    private View body;
    private CharSequence okText;
    private DialogInterface.OnClickListener ok;

    Dlg(Context c) {
        super(c);
    }

    @Override
    public AlertDialog.Builder setView(View v) {
        body = v;
        return super.setView(v);
    }

    @Override
    public AlertDialog.Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
        okText = text;
        ok = l;
        return super.setPositiveButton(text, null); // 직접 처리한다
    }

    @Override
    public AlertDialog.Builder setPositiveButton(int textId, DialogInterface.OnClickListener l) {
        return setPositiveButton(getContext().getText(textId), l);
    }

    private static boolean hasInput(View v) {
        if (v instanceof EditText) return v.isFocusable() || v.isFocusableInTouchMode() || true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasInput(g.getChildAt(i))) return true;
        }
        return false;
    }

    @Override
    public AlertDialog create() {
        final AlertDialog d = super.create();
        if (body != null && hasInput(body)) d.setCanceledOnTouchOutside(false);
        if (ok != null) {
            final DialogInterface.OnClickListener l = ok;
            d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Ui.rejected = false;
                l.onClick(d, AlertDialog.BUTTON_POSITIVE);
                if (!Ui.rejected && d.isShowing()) d.dismiss();
            }));
        }
        return d;
    }
}
