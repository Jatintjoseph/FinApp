package com.codex.financetracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_BILL = 7301;
    private static final int INK = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(92, 103, 122);
    private static final int SURFACE = Color.rgb(245, 247, 251);
    private static final int PAPER = Color.rgb(255, 255, 255);
    private static final int LINE = Color.rgb(224, 231, 240);
    private static final int BLUE = Color.rgb(0, 103, 255);
    private static final int SKY = Color.rgb(14, 165, 233);
    private static final int TEAL = Color.rgb(0, 125, 115);
    private static final int APP_MINT = Color.rgb(218, 247, 242);
    private static final int APP_ICE = Color.rgb(226, 241, 251);
    private static final int CASH_MINT = Color.rgb(0, 171, 158);
    private static final int CASH_BLUE = Color.rgb(24, 112, 210);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int CORAL = Color.rgb(229, 72, 77);
    private static final int GOLD = Color.rgb(183, 129, 34);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final String SOURCE_TRANSFER_FEE = "transfer_fee";
    private static final String SOURCE_TRANSFER_OUT = "transfer_out";
    private static final String SOURCE_TRANSFER_IN = "transfer_in";
    private static final String SOURCE_INVESTMENT_OPEN = "investment_open";
    private static final String SOURCE_INVESTMENT_ADD = "investment_add";
    private static final String SOURCE_INVESTMENT_GAIN = "investment_gain";
    private static final String SOURCE_INVESTMENT_MATURITY = "investment_maturity";
    private static final String SOURCE_BALANCE_ADJUSTMENT = "balance_adjustment";
    private static final String[] TABS = {
            "⌂ Home", "↕ Entries", "▦ Budgets", "↗ Investments",
            "▣ Balances", "◎ Goals", "⇄ Transfers", "✚ Categories"
    };

    private FinanceDb db;
    private LinearLayout root;
    private LinearLayout content;
    private long selectedCountryId;
    private int selectedTab = 0;
    private String pendingBillUri = "";
    private Button pendingBillButton;
    private final Set<Long> selectedAccountIds = new HashSet<Long>();
    private final Set<String> expandedEntryYears = new HashSet<String>();
    private final Set<String> expandedEntryMonths = new HashSet<String>();
    private float swipeStartX;
    private float swipeStartY;
    private String selectedEntryDay = "";
    private String selectedEntryMonth = "";
    private boolean showEntryArchive = false;
    private String selectedEntryReportType = "Expense";
    private Typeface interTypeface;
    private int currentDialogAccent = BLUE;
    private boolean sideMenuOpen = false;

    private interface DialogSubmit {
        boolean submit();
    }

    private interface DialogCancel {
        void cancel();
    }

    private interface RateFetchComplete {
        void complete(int saved);
    }

    private static class RateFetchUi {
        AlertDialog dialog;
        TextView status;
        TextView detail;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadFonts();
        db = new FinanceDb(this);
        db.ensureRuntimeSchema();
        selectedCountryId = db.firstCountryId();
        if (!db.isOnboarded()) {
            showOnboardingShell();
        } else {
            render();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BILL && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
                // Some document providers only grant a temporary read permission.
            }
            pendingBillUri = uri.toString();
            if (pendingBillButton != null) {
                String name = displayNameForUri(uri);
                pendingBillButton.setText(name.length() == 0 ? "▣ Bill attached" : "▣ " + name);
            }
            toast("Bill attached");
        }
    }

    private void showOnboardingShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(SURFACE);
        setContentView(root);

        TextView title = text("Finance Tracker", 30, Typeface.BOLD, INK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());
        TextView subtitle = text("A few details first, then your workspace opens.", 15, Typeface.NORMAL, MUTED);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        root.addView(subtitle, matchWrap());

        Button start = primaryButton("Start setup");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showOnboardingDialog();
            }
        });
        root.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        root.post(new Runnable() {
            @Override
            public void run() {
                showOnboardingDialog();
            }
        });
    }

    private void showOnboardingDialog() {
        LinearLayout form = form();
        final EditText firstName = input("What should I call you?", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final EditText countryName = input("Name of Account", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final Spinner currency = spinner(currencyLabels());
        setSpinnerToPrefix(currency, Locale.getDefault().getCountry().length() == 2
                ? currencyForCountry(Locale.getDefault().getCountry())
                : "USD");

        addDialogHero(form, "⌂", "First setup", "A few details first, then your workspace opens.", BLUE);
        form.addView(dialogSection("Your workspace", firstName, dialogFieldRow(countryName, currency)));

        final AlertDialog dialog = showStyledDialog(form, null, "Create workspace", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                String name = firstName.getText().toString().trim();
                String country = countryName.getText().toString().trim();
                if (name.length() == 0 || country.length() == 0) {
                    toast("Please add your name and first account.");
                    return false;
                }
                db.clearStarterCountriesIfEmpty();
                db.setMeta("first_name", name);
                selectedCountryId = db.addCountry(country, selectedCurrencyCode(currency));
                selectedTab = 0;
                render();
                return true;
            }
        });
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
    }

    private void render() {
        List<Country> countries = db.getCountries();
        if (countries.isEmpty()) {
            showNoCountryShell();
            return;
        }
        if (selectedCountryId == 0 || db.getCountry(selectedCountryId).id == 0) {
            selectedCountryId = countries.get(0).id;
        }

        if (sideMenuOpen) {
            LinearLayout shell = new LinearLayout(this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setBackground(revolutBackground());
            setContentView(shell);
            addSideMenu(shell);
            return;
        }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(revolutBackground());
        root.setPadding(dp(14), dp(10), dp(14), 0);
        setContentView(root);

        addHeader();

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(12), 0, dp(96));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (selectedTab == 0) showDashboard();
        if (selectedTab == 1) showEntries();
        if (selectedTab == 2) showBudgets();
        if (selectedTab == 3) showInvestments();
        if (selectedTab == 4) showBalances();
        if (selectedTab == 5) showGoals();
        if (selectedTab == 6) showTransfers();
        if (selectedTab == 7) showCategories();

        addBottomTabs();
    }

    private void showNoCountryShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackground(revolutBackground());
        setContentView(root);
        root.addView(text("Add an account to begin", 24, Typeface.BOLD, INK), matchWrap());
        Button add = primaryButton("＋ Add account");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCountryDialog(null);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(16), 0, 0);
        root.addView(add, params);
    }

    private void addHeader() {
        final Country country = db.getCountry(selectedCountryId);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, dp(4), 0, dp(10));
        root.addView(header, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        String firstName = db.getMeta("first_name");
        Button menu = menuButton("☰");
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sideMenuOpen = !sideMenuOpen;
                render();
            }
        });
        top.addView(menu, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(firstName.length() == 0 ? "Finance Tracker" : "Hi " + firstName, 23, Typeface.BOLD, INK);
        title.getPaint().setTextSkewX(-0.10f);
        copy.addView(title);
        int accent = currencyColor(country);
        TextView subtitle = text(country.name + " · " + currencySymbol(country.currency), 13, Typeface.BOLD, accent);
        subtitle.setPadding(dp(10), dp(5), dp(10), dp(5));
        subtitle.setBackground(round(tint(accent, 0.08f), dp(18), tint(accent, 0.24f)));
        copy.addView(subtitle);
        TextView hint = text("Swipe avatar to switch accounts.", 12, Typeface.NORMAL, MUTED);
        hint.setPadding(0, dp(5), 0, 0);
        copy.addView(hint);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.setMargins(dp(8), 0, 0, 0);
        top.addView(copy, copyParams);

        LinearLayout avatarBox = horizontal();
        avatarBox.setGravity(Gravity.CENTER);
        avatarBox.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextView avatar = profileAvatar(country, true);
        avatarBox.addView(avatar);
        attachAvatarSwitcher(avatarBox);
        top.addView(avatarBox, new LinearLayout.LayoutParams(dp(92), dp(58)));
        header.addView(top, matchWrap());
    }

    private void addBottomTabs() {
        Country country = db.getCountry(selectedCountryId);
        int accent = currencyColor(country);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(0, dp(10), 0, dp(20));
        shell.setBackground(round(Color.argb(238, 250, 255, 254), dp(22), Color.rgb(187, 219, 224)));
        shell.setElevation(dp(10));
        root.addView(shell, matchWrapWithMargins(0, dp(18), 0, dp(42)));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(9), 0, dp(9), 0);
        scroll.addView(row);
        shell.addView(scroll, matchWrap());

        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            Button button = bottomTabButton(TABS[i], selectedTab == i, accent);
            attachBottomTabTap(button, index);
            row.addView(button, bottomTabParams());
        }
    }

    private void attachBottomTabTap(Button button, final int index) {
        button.setOnTouchListener(new View.OnTouchListener() {
            float startX;
            float startY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = event.getX();
                    startY = event.getY();
                    view.setAlpha(0.82f);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    view.setAlpha(1f);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    view.setAlpha(1f);
                    float dx = Math.abs(event.getX() - startX);
                    float dy = Math.abs(event.getY() - startY);
                    if (dx < dp(18) && dy < dp(18)) {
                        selectedTab = index;
                        if (index == 1) {
                            selectedEntryDay = today();
                            selectedEntryMonth = monthToday();
                        }
                        showEntryArchive = false;
                        render();
                    }
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return true;
                }
                return true;
            }
        });
    }

    private void attachSwipeNavigation(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    swipeStartX = event.getX();
                    swipeStartY = event.getY();
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - swipeStartX;
                    float dy = event.getY() - swipeStartY;
                    if (Math.abs(dx) > dp(90) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        switchCountryBySwipe(dx < 0);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void attachAvatarSwitcher(final View avatar) {
        avatar.setOnTouchListener(new View.OnTouchListener() {
            private boolean moved;

            @Override
            public boolean onTouch(View touched, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    swipeStartX = event.getRawX();
                    swipeStartY = event.getRawY();
                    moved = false;
                    touched.setScaleX(0.96f);
                    touched.setScaleY(0.96f);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - swipeStartX;
                    float dy = event.getRawY() - swipeStartY;
                    if (Math.abs(dx) > dp(10) || Math.abs(dy) > dp(10)) moved = true;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    touched.setScaleX(1f);
                    touched.setScaleY(1f);
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    touched.setScaleX(1f);
                    touched.setScaleY(1f);
                    float dx = event.getRawX() - swipeStartX;
                    float dy = event.getRawY() - swipeStartY;
                    if (Math.abs(dx) > dp(24) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        switchCountryBySwipe(dx < 0);
                    } else if (!moved || (Math.abs(dx) < dp(12) && Math.abs(dy) < dp(12))) {
                        showCountrySwitcherDialog();
                    }
                    return true;
                }
                return true;
            }
        });
    }

    private void showCountrySwitcherDialog() {
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        LinearLayout panel = form();
        Country current = db.getCountry(selectedCountryId);
        int accent = currencyColor(current);
        addDialogHero(panel, currencySymbol(current.currency), "Accounts", "Tap to switch or add another account.", accent);

        List<Country> countries = db.getCountries();
        for (final Country item : countries) {
            LinearLayout row = countryAccountRow(item, dialogHolder);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectedCountryId = item.id;
                    selectedAccountIds.clear();
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    render();
                }
            });
            panel.addView(row);
        }

        Button add = primaryButton("＋ Add account");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                showCountryDialog(null);
            }
        });
        panel.addView(add, matchWrapWithMargins(0, dp(8), 0, 0));

        dialogHolder[0] = showStyledDialog(panel, "Close", null, accent, null, null);
    }

    private LinearLayout countryAccountRow(final Country country, final AlertDialog[] dialogHolder) {
        LinearLayout row = horizontal();
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        int accent = currencyColor(country);
        row.setBackground(round(country.id == selectedCountryId ? tint(accent, 0.10f) : Color.rgb(252, 253, 255),
                dp(14), country.id == selectedCountryId ? accent : Color.rgb(225, 230, 237)));
        LinearLayout.LayoutParams rowParams = matchWrapWithMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);
        row.setClickable(true);

        row.addView(profileAvatar(country, false));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(country.name, 16, Typeface.BOLD, INK));
        copy.addView(text(currencySymbol(country.currency) + " account", 13, Typeface.NORMAL, MUTED));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (country.id == selectedCountryId) {
            row.addView(text("✓", 22, Typeface.BOLD, accent));
        }
        Button edit = tinyIconButton("✎", accent);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                showCountryDialog(country);
            }
        });
        row.addView(edit, tinyActionParams());

        Button delete = tinyIconButton("🗑", CORAL);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!db.canDeleteCountry(country.id)) {
                    toast("This currency account has activity and cannot be deleted.");
                    return;
                }
                confirmDelete("Confirm deletion of " + country.name + " account?", new Runnable() {
                    @Override
                    public void run() {
                        db.deleteCountry(country.id);
                        if (selectedCountryId == country.id) selectedCountryId = db.firstCountryId();
                        selectedAccountIds.clear();
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                });
            }
        });
        row.addView(delete, tinyActionParams());
        return row;
    }

    private void switchCountryBySwipe(boolean next) {
        List<Country> countries = db.getCountries();
        if (countries.size() <= 1) {
            toast("Add another currency account to swipe between accounts.");
            return;
        }
        int index = selectedCountryIndex(countries);
        int newIndex = next ? index + 1 : index - 1;
        if (newIndex >= countries.size()) newIndex = 0;
        if (newIndex < 0) newIndex = countries.size() - 1;
        selectedCountryId = countries.get(newIndex).id;
        selectedAccountIds.clear();
        toast(countries.get(newIndex).name + " selected");
        render();
    }

    private int selectedCountryIndex(List<Country> countries) {
        for (int i = 0; i < countries.size(); i++) {
            if (countries.get(i).id == selectedCountryId) return i;
        }
        return 0;
    }

    private String countryDots(List<Country> countries) {
        StringBuilder builder = new StringBuilder();
        for (Country item : countries) {
            if (builder.length() > 0) builder.append("  ");
            builder.append(item.id == selectedCountryId ? "●" : "○");
        }
        return builder.toString();
    }

    private void addSideMenu(LinearLayout shell) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(14), dp(18), dp(12), dp(18));
        menu.setBackground(gradient(Color.rgb(250, 255, 254), Color.rgb(232, 246, 252), dp(0)));
        menu.setElevation(dp(10));
        shell.addView(menu, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Menu", 24, Typeface.BOLD, INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button close = tinyIconButton("×", CORAL);
        close.setTextSize(22);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sideMenuOpen = false;
                render();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        menu.addView(header, matchWrapWithMargins(0, 0, 0, dp(10)));
        addDrawerButton(menu, "↑ Income report", drawerReport("Income"));
        addDrawerButton(menu, "↓ Expense report", drawerReport("Expense"));
        addDrawerButton(menu, "▣ Tax flagged report", drawerReport("Tax"));
        addDrawerButton(menu, "↗ Investment report", drawerReport("Investments"));
        addDrawerButton(menu, "Reset all data", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sideMenuOpen = false;
                render();
                showResetDataDialog();
            }
        });
    }

    private View.OnClickListener drawerReport(final String reportType) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sideMenuOpen = false;
                render();
                showReportSetup(reportType);
            }
        };
    }

    private void addDrawerButton(LinearLayout menu, String title, View.OnClickListener listener) {
        Button button = outlineButton(title);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setTextColor(INK);
        button.setOnClickListener(listener);
        menu.addView(button, matchWrapWithMargins(0, dp(7), 0, 0));
    }

    private void showMainMenu() {
        sideMenuOpen = true;
        render();
    }

    private void addMenuButton(LinearLayout menu, String title, View.OnClickListener listener) {
        Button button = outlineButton(title);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setOnClickListener(listener);
        menu.addView(button, matchWrapWithMargins(0, dp(6), 0, 0));
    }

    private void showResetDataDialog() {
        LinearLayout form = form();
        addDialogHero(form, "!", "Reset everything", "This will delete every entry, account, currency account, transfer, goal, budget, investment, rate and saved setup detail.", CORAL);
        form.addView(dialogSection("Start over", text("The app will return to the first setup screen. This cannot be undone.", 14, Typeface.BOLD, CORAL)));
        showStyledDialog(form, "Cancel", "Reset", CORAL, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                db.resetAllData();
                selectedCountryId = 0;
                selectedTab = 0;
                selectedAccountIds.clear();
                expandedEntryYears.clear();
                expandedEntryMonths.clear();
                selectedEntryDay = "";
                selectedEntryMonth = "";
                showEntryArchive = false;
                selectedEntryReportType = "Expense";
                pendingBillUri = "";
                pendingBillButton = null;
                showOnboardingShell();
                toast("All data reset.");
                return true;
            }
        });
    }

    private int reportAccent(String reportType) {
        if ("Income".equals(reportType)) return GREEN;
        if ("Expense".equals(reportType)) return CORAL;
        if ("Tax".equals(reportType)) return GOLD;
        return PURPLE;
    }

    private String reportIcon(String reportType) {
        if ("Income".equals(reportType)) return "↑";
        if ("Expense".equals(reportType)) return "↓";
        if ("Tax".equals(reportType)) return "▣";
        return "↗";
    }

    private void showReportSetup(final String reportType) {
        LinearLayout form = form();
        final EditText start = dateInput("Start date", monthToday() + "-01", false);
        final EditText end = dateInput("End date", today(), false);
        final List<Category> categories = ("Income".equals(reportType) || "Expense".equals(reportType))
                ? db.categories(selectedCountryId, reportType)
                : new ArrayList<Category>();
        final List<CheckBox> checks = new ArrayList<CheckBox>();
        int accent = reportAccent(reportType);
        addDialogHero(form, reportIcon(reportType), reportType + " report", "Choose the period and categories to include.", accent);
        form.addView(dialogSection("Period", dialogFieldRow(start, end)));
        if (!categories.isEmpty()) {
            form.addView(label("Categories"));
            for (Category category : categories) {
                CheckBox check = new CheckBox(this);
                styleDialogCheckBox(check, category.icon + " " + category.name);
                check.setChecked(true);
                checks.add(check);
                form.addView(check, matchWrapWithMargins(0, checks.size() == 1 ? 0 : dp(8), 0, 0));
            }
        }

        showStyledDialog(form, "Cancel", "Generate", accent, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                String selectedCategories = checkedCategoryNames(categories, checks);
                showReportResult(reportType, clean(start.getText().toString(), monthToday() + "-01"),
                        clean(end.getText().toString(), today()), selectedCategories);
                return true;
            }
        });
    }

    private void showReportResult(String reportType, String start, String end, String categories) {
        Country country = db.getCountry(selectedCountryId);
        String report;
        if ("Investments".equals(reportType)) {
            report = db.investmentReport(selectedCountryId, start, end, country.currency);
        } else if ("Tax".equals(reportType)) {
            report = db.entryReport(selectedCountryId, "", start, end, "", true, country.currency);
        } else {
            report = db.entryReport(selectedCountryId, reportType, start, end, categories, false, country.currency);
        }
        LinearLayout form = form();
        int accent = reportAccent(reportType);
        addDialogHero(form, reportIcon(reportType), reportType + " report", start + " to " + end, accent);
        TextView body = text(report, 13, Typeface.NORMAL, INK);
        body.setTextIsSelectable(true);
        form.addView(dialogSection("Report", body));
        showStyledDialog(form, null, "Done", accent, null, null);
    }

    private void showDashboard() {
        Country country = db.getCountry(selectedCountryId);
        addSectionTitle("This Month");
        double[] summary = db.summaryForPeriod(selectedCountryId, monthToday() + "-01", monthToday() + "-31");
        content.addView(dashboardFlowCard(summary, country));
        content.addView(taxMarkedCard(country, monthToday() + "-01", monthToday() + "-31"));

        addSectionTitle("Budgets This Month");
        List<BudgetProgress> budgets = db.budgetProgress(selectedCountryId, monthToday());
        if (budgets.isEmpty()) {
            LinearLayout emptyBudget = cardWith(emptyText("No budget set for " + monthToday() + "."));
            makeCardNavigate(emptyBudget, 2);
            content.addView(emptyBudget);
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 0, dp(4), 0);
            scroll.addView(row);
            for (BudgetProgress budget : budgets) row.addView(budgetMiniCard(budget, country.currency));
            content.addView(scroll, matchWrapWithMargins(0, 0, 0, dp(8)));
        }

        addSectionTitle("Investments");
        LinearLayout investmentCard = card();
        makeCardNavigate(investmentCard, 3);
        investmentCard.addView(text("Total invested till date", 15, Typeface.BOLD, INK));
        double[] invested = db.investedByType(selectedCountryId);
        investmentCard.addView(metricRow("🏦 Fixed Deposits", money(invested[0], country.currency), TEAL));
        investmentCard.addView(metricRow("🔁 Recurring Deposits", money(invested[1], country.currency), TEAL));
        investmentCard.addView(metricRow("📈 Market Linked Funds", money(invested[2], country.currency), TEAL));
        investmentCard.addView(divider());
        investmentCard.addView(metricRow("Total", money(invested[0] + invested[1] + invested[2], country.currency), INK));
        content.addView(investmentCard);

        addSectionTitle("Account Balances");
        content.addView(accountBalanceCard(country));

        addSectionTitle("Goals");
        List<Goal> goals = db.goals(selectedCountryId);
        if (goals.isEmpty()) {
            LinearLayout emptyGoal = cardWith(emptyText("No goals yet."));
            makeCardNavigate(emptyGoal, 5);
            content.addView(emptyGoal);
        } else {
            boolean hasActive = false;
            for (Goal goal : goals) {
                if (goal.currentAmount < goal.targetAmount) {
                    content.addView(goalCard(goal, country.currency, false));
                    hasActive = true;
                }
            }
            if (!hasActive) content.addView(cardWith(emptyText("No goals in progress.")));
            boolean hasCompleted = false;
            for (Goal goal : goals) if (goal.currentAmount >= goal.targetAmount) hasCompleted = true;
            if (hasCompleted) {
                addSectionTitle("Completed Goals");
                for (Goal goal : goals) if (goal.currentAmount >= goal.targetAmount) content.addView(goalCard(goal, country.currency, false));
            }
        }
    }

    private void showEntries() {
        final Country country = db.getCountry(selectedCountryId);
        db.ensureRuntimeSchema();
        addSectionTitle("Income & Expenses");
        LinearLayout split = horizontal();
        LinearLayout incomePanel = entryActionPanel("Income", "＋", GREEN);
        incomePanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEntryDialog("Income");
            }
        });
        LinearLayout expensePanel = entryActionPanel("Expense", "＋", CORAL);
        expensePanel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEntryDialog("Expense");
            }
        });
        split.addView(incomePanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams expenseParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        expenseParams.setMargins(dp(8), 0, 0, 0);
        split.addView(expensePanel, expenseParams);
        content.addView(split, matchWrapWithMargins(0, 0, 0, dp(8)));

        List<Entry> entries = db.entriesSince(selectedCountryId, fiveYearsAgo());
        if (selectedEntryDay.length() == 0) selectedEntryDay = today();
        if (selectedEntryReportType.length() == 0) selectedEntryReportType = "Expense";
        List<PendingRecurringEntry> pendingRecurring = pendingRecurringForDate(pendingRecurringEntries(entries), selectedEntryDay);
        if (!pendingRecurring.isEmpty()) content.addView(pendingRecurringCard(pendingRecurring, country));
        content.addView(dailyEntryReport(entries, country));
    }

    private List<PendingRecurringEntry> pendingRecurringEntries(List<Entry> entries) {
        List<PendingRecurringEntry> result = new ArrayList<PendingRecurringEntry>();
        String cutoffDate = recurringDueCutoffDate();
        for (Entry entry : entries) {
            if (entry.id <= 0 || entry.repeatInterval <= 0 || entry.repeatUnit == null || entry.repeatUnit.length() == 0) {
                continue;
            }
            int limit = entry.repeatCount > 0 ? entry.repeatCount : 60;
            for (int occurrence = 1; occurrence <= limit; occurrence++) {
                String occurrenceDate = recurringOccurrenceDate(entry, occurrence);
                if (occurrenceDate.length() == 0) continue;
                if (db.recurringOccurrenceExists(selectedCountryId, entry, occurrenceDate)) continue;
                PendingRecurringEntry pending = new PendingRecurringEntry();
                pending.template = entry;
                pending.date = occurrenceDate;
                pending.occurrence = occurrence;
                pending.approvable = occurrenceDate.compareTo(cutoffDate) <= 0;
                result.add(pending);
                if (!pending.approvable) break;
            }
        }
        Collections.sort(result, new Comparator<PendingRecurringEntry>() {
            @Override
            public int compare(PendingRecurringEntry left, PendingRecurringEntry right) {
                int date = safeText(left.date).compareTo(safeText(right.date));
                if (date != 0) return date;
                return safeText(left.template.title).compareTo(safeText(right.template.title));
            }
        });
        return result;
    }

    private List<PendingRecurringEntry> pendingRecurringForDate(List<PendingRecurringEntry> entries, String date) {
        List<PendingRecurringEntry> result = new ArrayList<PendingRecurringEntry>();
        for (PendingRecurringEntry entry : entries) {
            if (date.equals(entry.date)) result.add(entry);
        }
        return result;
    }

    private LinearLayout pendingRecurringCard(List<PendingRecurringEntry> pendingRecurring, Country country) {
        LinearLayout card = card();
        card.setBackground(round(Color.rgb(242, 246, 251), dp(18), Color.rgb(211, 221, 234)));
        LinearLayout header = horizontal();
        header.addView(iconBubble("🔁", BLUE, tint(BLUE, 0.10f)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Repeating entries to confirm", 17, Typeface.BOLD, INK));
        copy.addView(text("Upcoming terms are shown muted and affect balances only after approval.", 12, Typeface.NORMAL, MUTED));
        header.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(header);
        for (PendingRecurringEntry pending : pendingRecurring) {
            card.addView(pendingRecurringRow(pending, country), matchWrapWithMargins(0, dp(8), 0, 0));
        }
        return card;
    }

    private LinearLayout pendingRecurringRow(final PendingRecurringEntry pending, final Country country) {
        final Entry template = pending.template;
        boolean income = "Income".equals(template.type);
        final int accent = income ? GREEN : CORAL;
        LinearLayout row = horizontal();
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        int fill = pending.approvable ? Color.rgb(248, 250, 253) : Color.rgb(252, 253, 255);
        int stroke = pending.approvable ? Color.rgb(220, 226, 236) : Color.rgb(228, 232, 238);
        row.setBackground(round(fill, dp(14), stroke));
        row.addView(iconBubble(db.categoryIcon(selectedCountryId, template.category), MUTED, Color.rgb(232, 236, 243)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(template.title, 15, Typeface.BOLD, INK));
        copy.addView(text((pending.approvable ? "Ready: " : "Upcoming: ") + dayHeader(pending.date) + " · " + template.accountName + " · " + repeatCompactLabel(template), 11, Typeface.NORMAL, MUTED));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout action = new LinearLayout(this);
        action.setOrientation(LinearLayout.VERTICAL);
        TextView amount = text((income ? "+" : "-") + money(template.amount, country.currency), 13, Typeface.BOLD, MUTED);
        amount.setGravity(Gravity.END);
        action.addView(amount);
        Button edit = outlineButton("Edit");
        edit.setTextSize(11);
        edit.setTextColor(BLUE);
        edit.setBackground(round(tint(BLUE, 0.07f), dp(14), tint(BLUE, 0.24f)));
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEntryDialog(template.type, template);
            }
        });
        action.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        Button confirm = outlineButton(pending.approvable ? (income ? "Received" : "Incurred") : "Waiting");
        confirm.setTextColor(pending.approvable ? accent : MUTED);
        confirm.setTextSize(11);
        confirm.setEnabled(pending.approvable);
        confirm.setAlpha(pending.approvable ? 1f : 0.55f);
        confirm.setBackground(round(pending.approvable ? tint(accent, 0.08f) : Color.rgb(245, 247, 250),
                dp(14), pending.approvable ? tint(accent, 0.24f) : Color.rgb(226, 231, 238)));
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!pending.approvable) {
                    toast("This recurring entry can be approved on or after " + pending.date + ".");
                    return;
                }
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        db.addEntry(selectedCountryId, template.type, pending.date, template.title, template.amount,
                                template.category, "", template.accountId, template.notes, "",
                                template.taxFlag, template.taxAmount, template.taxTitle, 0, "", 0);
                        toast(template.type + " confirmed.");
                        render();
                    }
                };
                Account account = db.account(template.accountId);
                if ("Expense".equals(template.type) && needsOutflowConfirmation(account, template.amount)) {
                    showBalanceConfirmation("Confirm recurring expense", outflowWarning(account, template.amount), CORAL, save);
                    return;
                }
                save.run();
            }
        });
        action.addView(confirm, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        row.addView(action);
        return row;
    }

    private void showBudgets() {
        final Country country = db.getCountry(selectedCountryId);
        addActionHeader("Budgets", "＋ Add budget", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBudgetDialog();
            }
        });

        List<BudgetProgress> budgets = db.budgetProgress(selectedCountryId, null);
        if (budgets.isEmpty()) {
            content.addView(cardWith(emptyText("No budgets yet.")));
            return;
        }
        for (BudgetProgress budget : budgets) content.addView(budgetCard(budget, country.currency, true));
    }

    private void showInvestments() {
        final Country country = db.getCountry(selectedCountryId);
        addActionHeader("FDs, RDs & Market Funds", "＋ Add investment", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInvestmentDialog();
            }
        });

        List<Investment> investments = db.investments(selectedCountryId);
        if (investments.isEmpty()) {
            content.addView(cardWith(emptyText("No fixed deposits, recurring deposits, or market linked funds yet.")));
            return;
        }
        boolean hasActive = false;
        for (final Investment investment : investments) {
            if (!"Matured".equals(investment.status)) {
                content.addView(investmentCard(investment, country));
                hasActive = true;
            }
        }
        if (!hasActive) content.addView(cardWith(emptyText("No investments in progress.")));
        boolean hasCompleted = false;
        for (Investment investment : investments) {
            if ("Matured".equals(investment.status)) hasCompleted = true;
        }
        if (hasCompleted) {
            addSectionTitle("Completed investments");
            for (final Investment investment : investments) {
                if ("Matured".equals(investment.status)) content.addView(investmentCard(investment, country));
            }
        }
    }

    private LinearLayout investmentCard(final Investment investment, final Country country) {
        LinearLayout card = card();
        LinearLayout title = horizontal();
        title.addView(iconBubble(investmentIcon(investment.type)));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(investment.title, 18, Typeface.BOLD, INK));
        details.addView(text(investment.type + " · " + investment.accountName, 13, Typeface.NORMAL, MUTED));
        title.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(title);
        card.addView(metricRow("Invested principal", money(investment.principal, country.currency), INK));
        card.addView(metricRow("Present value", money(investment.currentValue, country.currency), TEAL));
        card.addView(metricRow("Maturity value", money(investment.maturityValue, country.currency), GREEN));
        card.addView(text("Opened: " + investment.startDate + " · Maturity: " + blankAsDash(investment.maturityDate), 12, Typeface.NORMAL, MUTED));
        if (!"Market Linked Fund".equals(investment.type)) {
            card.addView(text("Instructions: " + investment.maturityAction + " · Status: " + investment.status, 12, Typeface.NORMAL, MUTED));
        } else {
            card.addView(text("Status: " + investment.status, 12, Typeface.NORMAL, MUTED));
        }
        if (investment.notes.length() > 0) card.addView(text(investment.notes, 13, Typeface.NORMAL, INK));
        if (!"Matured".equals(investment.status)) {
            LinearLayout actions = horizontal();
            Button editAmount = outlineButton("Edit amount");
            editAmount.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEditInvestmentAmountDialog(investment);
                }
            });
            actions.addView(editAmount, smallActionParams());
            if (!"Fixed Deposit".equals(investment.type)) {
                Button addFunds = outlineButton("＋ Add funds");
                addFunds.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showAddInvestmentFundsDialog(investment);
                    }
                });
                actions.addView(addFunds, smallActionParams());
            }
            Button mature = outlineButton("Close");
            mature.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if ("Market Linked Fund".equals(investment.type)) showMarketFundMaturityDialog(investment);
                    else showMaturityDialog(investment);
                }
            });
            actions.addView(mature, smallActionParams());
            card.addView(actions);
        }
        Button delete = outlineButton("Delete");
        delete.setTextColor(CORAL);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmDelete("Delete this investment?", new Runnable() {
                    @Override
                    public void run() {
                        db.deleteInvestment(investment);
                        render();
                    }
                });
            }
        });
        card.addView(delete, smallActionParams());
        return card;
    }

    private void showBalances() {
        final Country country = db.getCountry(selectedCountryId);
        addActionHeader("Accounts", "＋ Add account", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAccountDialog();
            }
        });
        content.addView(homeAccountSelectionCard(country));

        List<Account> accounts = db.accounts(selectedCountryId);
        if (accounts.isEmpty()) {
            content.addView(cardWith(emptyText("No accounts yet.")));
            return;
        }
        for (final Account account : accounts) {
            double goalReserved = db.goalReservedForAccount(account.id);
            LinearLayout card = card();
            LinearLayout row = horizontal();
            row.addView(iconBubble(accountIcon(account.type)));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.addView(text(account.name, 18, Typeface.BOLD, INK));
            details.addView(text(account.type + " · " + currencySymbol(account.currency), 13, Typeface.NORMAL, MUTED));
            row.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button edit = tinyIconButton("✎", BLUE);
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showAccountDialog(account);
                }
            });
            row.addView(edit, tinyActionParams());
            Button delete = tinyIconButton("🗑", CORAL);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (db.accountHasActivity(account.id)) {
                        toast("This account has entries, transfers, investments, or goals.");
                        return;
                    }
                    confirmDelete("Delete this account?", new Runnable() {
                        @Override
                        public void run() {
                            db.deleteById("accounts", account.id);
                            selectedAccountIds.remove(account.id);
                            render();
                        }
                    });
                }
            });
            row.addView(delete, tinyActionParams());
            card.addView(row);
            card.addView(text(money(account.balance, account.currency), 24, Typeface.BOLD, account.balance >= 0 ? TEAL : CORAL));
            if (goalReserved > 0) {
                card.addView(metricRow("Set aside for goals", money(goalReserved, account.currency), BLUE));
                card.addView(metricRow("Available", money(account.balance - goalReserved, account.currency), account.balance - goalReserved >= 0 ? TEAL : CORAL));
            }
            Button history = outlineButton("Entries");
            history.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showAccountEntriesDialog(account);
                }
            });
            card.addView(history, smallActionParams());
            content.addView(card);
        }
    }

    private LinearLayout homeAccountSelectionCard(final Country country) {
        LinearLayout card = card();
        int accent = currencyColor(country);
        card.setBackground(round(tint(accent, 0.07f), dp(8), tint(accent, 0.22f)));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAccountSelectionDialog(country);
            }
        });
        LinearLayout row = horizontal();
        row.addView(iconBubble("▣", accent, tint(accent, 0.12f)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Accounts shown on Home", 16, Typeface.BOLD, INK));
        copy.addView(text("Tap to choose which accounts feed the Home balance.", 12, Typeface.NORMAL, MUTED));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text("›", 24, Typeface.BOLD, accent));
        card.addView(row);
        return card;
    }

    private void showAccountEntriesDialog(Account account) {
        Country country = db.getCountry(selectedCountryId);
        List<Entry> entries = db.accountEntries(selectedCountryId, account.id);
        LinearLayout form = form();
        addDialogHero(form, accountIcon(account.type), account.name, "Income and expense entries linked to this account.", BLUE);
        if (entries.isEmpty()) {
            form.addView(emptyText("No entries linked to this account."));
        } else {
            for (Entry entry : entries) {
                LinearLayout row = horizontal();
                boolean income = "Income".equals(entry.type);
                int color = income ? GREEN : CORAL;
                row.setPadding(dp(10), dp(10), dp(10), dp(10));
                row.setBackground(round(tint(color, 0.05f), dp(12), tint(color, 0.18f)));
                LinearLayout copy = new LinearLayout(this);
                copy.setOrientation(LinearLayout.VERTICAL);
                copy.addView(text(entry.title, 14, Typeface.BOLD, INK));
                copy.addView(text(entry.date + " · " + entry.category, 11, Typeface.NORMAL, MUTED));
                row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView value = text((income ? "+" : "-") + money(entry.amount, country.currency), 13, Typeface.BOLD, color);
                value.setGravity(Gravity.END);
                row.addView(value);
                form.addView(row, matchWrapWithMargins(0, 0, 0, dp(8)));
            }
        }
        showStyledDialog(form, null, "Done", BLUE, null, null);
    }

    private void showGoals() {
        final Country country = db.getCountry(selectedCountryId);
        addActionHeader("Goals", "＋ Add goal", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showGoalDialog();
            }
        });

        List<Goal> goals = db.goals(selectedCountryId);
        if (goals.isEmpty()) {
            content.addView(cardWith(emptyText("No goals yet.")));
            return;
        }
        boolean hasActive = false;
        for (Goal goal : goals) {
            if (goal.currentAmount < goal.targetAmount) {
                content.addView(goalCard(goal, country.currency, true));
                hasActive = true;
            }
        }
        if (!hasActive) content.addView(cardWith(emptyText("No goals in progress.")));
        boolean hasCompleted = false;
        for (Goal goal : goals) if (goal.currentAmount >= goal.targetAmount) hasCompleted = true;
        if (hasCompleted) {
            addSectionTitle("Completed goals");
            for (Goal goal : goals) {
                if (goal.currentAmount >= goal.targetAmount) content.addView(goalCard(goal, country.currency, true));
            }
        }
    }

    private void showTransfers() {
        addActionHeader("Transfers", "⇄ Send money", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showTransferDialog();
            }
        });

        addSectionTitle("Recent Transfers");
        List<Transfer> transfers = db.transfers();
        if (transfers.isEmpty()) {
            content.addView(cardWith(emptyText("No transfers yet.")));
            return;
        }
        for (final Transfer transfer : transfers) {
            LinearLayout card = card();
            card.addView(text("⇄ " + transfer.fromCountry + " to " + transfer.toCountry, 17, Typeface.BOLD, INK));
            card.addView(metricRow("Expense from " + transfer.fromAccount, money(transfer.fromAmount, transfer.fromCurrency), CORAL));
            if (transfer.feeAmount > 0) card.addView(metricRow("Fee", money(transfer.feeAmount, transfer.fromCurrency), GOLD));
            card.addView(metricRow("Income to " + transfer.toAccount, money(transfer.toAmount, transfer.toCurrency), GREEN));
            card.addView(text("Rate: " + oneFour(transfer.rate) + " · " + clean(transfer.rateSource, "Manual") + " · " + transfer.date, 12, Typeface.NORMAL, MUTED));
            if (transfer.notes.length() > 0) card.addView(text(transfer.notes, 13, Typeface.NORMAL, INK));
            LinearLayout actions = horizontal();
            Button edit = outlineButton("Edit");
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showTransferDialog(transfer);
                }
            });
            actions.addView(edit, smallActionParams());
            Button delete = outlineButton("Delete");
            delete.setTextColor(CORAL);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete("Delete this transfer?", new Runnable() {
                        @Override
                        public void run() {
                            db.deleteTransfer(transfer.id);
                            render();
                        }
                    });
                }
            });
            actions.addView(delete, smallActionParams());
            card.addView(actions);
            content.addView(card);
        }
    }

    private void showCategories() {
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Categories & Icons", 22, Typeface.BOLD, INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button edit = tinyIconButton("✎", BLUE);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCategorySelectionDialog(false);
            }
        });
        header.addView(edit, tinyActionParams());
        Button delete = tinyIconButton("🗑", CORAL);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCategorySelectionDialog(true);
            }
        });
        header.addView(delete, tinyActionParams());
        content.addView(header, matchWrap());
        content.addView(spacer(10));

        List<Category> categories = db.categories(selectedCountryId);
        LinearLayout split = horizontal();
        LinearLayout incomeColumn = categoryColumn("Income", GREEN, "Income");
        LinearLayout expenseColumn = categoryColumn("Expense", CORAL, "Expense");
        boolean hasIncome = false;
        boolean hasExpense = false;
        for (final Category category : categories) {
            if ("Income".equals(category.type)) {
                incomeColumn.addView(categoryTile(category, GREEN));
                hasIncome = true;
            } else {
                expenseColumn.addView(categoryTile(category, CORAL));
                hasExpense = true;
            }
        }
        if (!hasIncome) incomeColumn.addView(emptyText("No income categories."));
        if (!hasExpense) expenseColumn.addView(emptyText("No expense categories."));

        split.setGravity(Gravity.TOP);

        LinearLayout.LayoutParams incomeParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        incomeParams.gravity = Gravity.TOP;
        split.addView(incomeColumn, incomeParams);
        LinearLayout.LayoutParams expenseParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        expenseParams.setMargins(dp(8), 0, 0, 0);
        expenseParams.gravity = Gravity.TOP;
        split.addView(expenseColumn, expenseParams);
        content.addView(split, matchWrap());
    }

    private LinearLayout categoryColumn(String title, int color, final String type) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(8), dp(8), dp(8), dp(8));
        column.setBackground(round(tint(color, 0.06f), dp(8), tint(color, 0.22f)));
        LinearLayout header = horizontal();
        TextView heading = text(title, 17, Typeface.BOLD, color);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button add = tinyIconButton("＋", color);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCategoryDialog(null, type);
            }
        });
        header.addView(add, tinyActionParams());
        column.addView(header, matchWrapWithMargins(0, 0, 0, dp(8)));
        return column;
    }

    private LinearLayout categoryTile(final Category category, int color) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(round(PAPER, dp(8), tint(color, 0.22f)));
        tile.setElevation(dp(2));
        tile.setLayoutParams(matchWrapWithMargins(0, 0, 0, dp(8)));
        TextView icon = text(category.icon, 18, Typeface.NORMAL, color);
        icon.setGravity(Gravity.CENTER);
        tile.addView(icon, matchWrap());
        TextView name = text(category.name, 12, Typeface.BOLD, INK);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(false);
        tile.addView(name, matchWrap());
        return tile;
    }

    private void showCategorySelectionDialog(final boolean deleteMode) {
        if (db.categories(selectedCountryId).isEmpty()) {
            toast("No categories available.");
            return;
        }
        LinearLayout form = form();
        final Spinner type = spinner(new String[]{"Income", "Expense"});
        final List<Category>[] visibleCategories = new List[]{db.categories(selectedCountryId, "Income")};
        final Spinner category = spinner(categoryRemoveLabels(visibleCategories[0]));
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                visibleCategories[0] = db.categories(selectedCountryId, type.getSelectedItem().toString());
                resetSpinner(category, categoryRemoveLabels(visibleCategories[0]));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        int accent = deleteMode ? CORAL : BLUE;
        addDialogHero(form, deleteMode ? "🗑" : "✎",
                deleteMode ? "Delete category" : "Edit category",
                "Choose income or expense first, then select the category.", accent);
        form.addView(dialogSection("Category type", type, category));
        showStyledDialog(form, "Cancel", deleteMode ? "Delete" : "Edit", accent, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                if (visibleCategories[0].isEmpty()) {
                    toast("No " + type.getSelectedItem().toString().toLowerCase(Locale.US) + " categories available.");
                    return false;
                }
                final Category selected = visibleCategories[0].get(category.getSelectedItemPosition());
                if (!deleteMode) {
                    showCategoryDialog(selected, selected.type);
                    return true;
                }
                confirmDelete("Delete this category?", new Runnable() {
                    @Override
                    public void run() {
                        db.removeCategory(selectedCountryId, selected);
                        render();
                    }
                });
                return true;
            }
        });
    }

    private void showRemoveCategoryDialog() {
        final List<Category> categories = db.categories(selectedCountryId);
        if (categories.isEmpty()) {
            toast("No categories to remove.");
            return;
        }
        LinearLayout form = form();
        final Spinner category = spinner(categoryRemoveLabels(categories));
        addDialogHero(form, "✚", "Remove category", "Remove it from category choices without changing old entries.", CORAL);
        form.addView(dialogSection("Category", category));
        showStyledDialog(form, "Cancel", "Remove", CORAL, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                Category selected = categories.get(category.getSelectedItemPosition());
                db.removeCategory(selectedCountryId, selected);
                render();
                return true;
            }
        });
    }

    private LinearLayout accountBalanceCard(final Country country) {
        int accent = currencyColor(country);
        LinearLayout card = card();
        card.setClickable(true);
        card.setBackground(round(tint(accent, 0.08f), dp(8), tint(accent, 0.25f)));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedTab = 4;
                render();
            }
        });
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(iconBubble("▣", accent, tint(accent, 0.12f)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Account balances", 16, Typeface.BOLD, INK));
        copy.addView(text("Tap to manage accounts.", 12, Typeface.NORMAL, MUTED));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        copyParams.setMargins(dp(10), 0, 0, 0);
        header.addView(copy, copyParams);
        TextView chevron = text("›", 28, Typeface.BOLD, accent);
        chevron.setGravity(Gravity.CENTER);
        header.addView(chevron, new LinearLayout.LayoutParams(dp(28), dp(40)));
        card.addView(header);

        List<Account> accounts = db.accounts(selectedCountryId);
        ensureAccountSelection(accounts);
        if (accounts.isEmpty()) {
            card.addView(emptyText("No accounts yet. Add your first account from Balances."));
            return card;
        }
        card.addView(spacer(10));
        double totalValue = db.selectedAccountTotal(selectedAccountIds);
        double reservedValue = db.selectedGoalReserved(selectedAccountIds);
        double availableValue = totalValue - reservedValue;
        card.addView(metricRow("Total balance in account", money(totalValue, country.currency), INK));
        card.addView(metricRow("Money set aside for goals", money(reservedValue, country.currency), BLUE));
        TextView available = text("Available " + money(availableValue, country.currency), 28, Typeface.BOLD, availableValue >= 0 ? TEAL : CORAL);
        available.setPadding(0, dp(8), 0, dp(4));
        card.addView(available);
        String other = db.otherCountriesAvailableSummary(selectedCountryId);
        if (other.length() > 0) card.addView(text("Other currency available: " + other, 12, Typeface.BOLD, MUTED));
        return card;
    }

    private String selectedAccountCountText(List<Account> accounts) {
        int selected = 0;
        for (Account account : accounts) {
            if (selectedAccountIds.contains(account.id)) selected++;
        }
        if (selected == accounts.size()) return "Showing all accounts";
        if (selected == 1) return "Showing 1 selected account";
        return "Showing " + selected + " selected accounts";
    }

    private void showAccountSelectionDialog(final Country country) {
        final List<Account> accounts = db.accounts(selectedCountryId);
        ensureAccountSelection(accounts);
        LinearLayout form = form();
        final int accent = currencyColor(country);
        addDialogHero(form, "▣", "Choose balances", "Pick the accounts to combine in this view.", accent);
        final List<CheckBox> checks = new ArrayList<CheckBox>();
        if (accounts.isEmpty()) {
            form.addView(dialogSection("Accounts", emptyText("No accounts yet. Add your first account from Balances.")));
        } else {
            for (Account account : accounts) {
                double reserved = db.goalReservedForAccount(account.id);
                CheckBox check = new CheckBox(this);
                String checkText = accountIcon(account.type) + " " + account.name + " · " + money(account.balance, account.currency);
                if (reserved > 0) checkText += " · " + money(reserved, account.currency) + " set aside";
                styleDialogCheckBox(check, checkText);
                check.setChecked(selectedAccountIds.contains(account.id));
                checks.add(check);
                form.addView(check, matchWrapWithMargins(0, checks.size() == 1 ? 0 : dp(8), 0, 0));
            }
        }

        showStyledDialog(form, "Cancel", "Apply", accent, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                if (accounts.isEmpty()) return true;
                Set<Long> next = new HashSet<Long>();
                for (int i = 0; i < accounts.size() && i < checks.size(); i++) {
                    if (checks.get(i).isChecked()) next.add(accounts.get(i).id);
                }
                if (next.isEmpty()) {
                    toast("Choose at least one account.");
                    return false;
                }
                selectedAccountIds.clear();
                selectedAccountIds.addAll(next);
                render();
                return true;
            }
        });
    }

    private void updateSelectedAccountSummary(TextView total, TextView reserved, TextView available, Country country) {
        double totalValue = db.selectedAccountTotal(selectedAccountIds);
        double reservedValue = db.selectedGoalReserved(selectedAccountIds);
        total.setText(money(totalValue, country.currency));
        reserved.setText(reservedValue > 0 ? "Set aside for goals: " + money(reservedValue, country.currency) : "No money set aside for goals in selected accounts");
        available.setText("Available: " + money(totalValue - reservedValue, country.currency));
        available.setTextColor(totalValue - reservedValue >= 0 ? TEAL : CORAL);
    }

    private void showEntryDialog(final String entryType) {
        showEntryDialog(entryType, null);
    }

    private void showEntryDialog(final String entryType, final Entry existing) {
        pendingBillUri = "";
        pendingBillButton = null;
        final Country country = db.getCountry(selectedCountryId);
        final List<Account>[] accounts = new List[]{db.accounts(selectedCountryId)};
        final List<Category>[] categories = new List[]{db.categories(selectedCountryId, entryType)};

        LinearLayout form = form();
        int accent = "Income".equals(entryType) ? GREEN : CORAL;
        addDialogHero(form, "Income".equals(entryType) ? "↑" : "↓",
                existing == null ? "Add " + entryType.toLowerCase(Locale.US) : "Edit " + entryType.toLowerCase(Locale.US),
                "Capture amount, account, tax and repeat details.", accent);
        final EditText date = dateInput("Date", existing == null ? today() : existing.date, false);
        final AutoCompleteTextView title = titleInput(entryType);
        if (existing != null) title.setText(existing.title);
        final EditText amount = input("Amount in " + currencySymbol(country.currency), existing == null ? "" : moneyPlain(existing.amount), decimalInput());
        final Spinner category = spinner(categoryLabels(categories[0]));
        final Spinner account = spinner(entryAccountNames(accounts[0]));
        final EditText notes = multiInput("Notes");
        final CheckBox tax = new CheckBox(this);
        if (existing != null) notes.setText(existing.notes);
        final String[] taxValues = {existing == null || !existing.taxFlag ? "" : moneyPlain(existing.taxAmount),
                existing == null ? "" : existing.taxTitle};
        final boolean[] accountPickerReady = new boolean[]{false};
        final boolean[] refreshingAccounts = new boolean[]{false};
        account.post(new Runnable() {
            @Override
            public void run() {
                accountPickerReady[0] = true;
            }
        });
        account.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (!accountPickerReady[0] || refreshingAccounts[0]) return;
                if (position == accounts[0].size()) {
                    showAccountDialog(new Runnable() {
                        @Override
                        public void run() {
                            accounts[0] = db.accounts(selectedCountryId);
                            refreshingAccounts[0] = true;
                            resetSpinner(account, entryAccountNames(accounts[0]));
                            if (!accounts[0].isEmpty()) account.setSelection(accounts[0].size() - 1);
                            refreshingAccounts[0] = false;
                        }
                    });
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        title.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                EntryTemplate template = db.templateForTitle(selectedCountryId, entryType, title.getText().toString());
                if (template.category.length() > 0) setCategorySpinner(category, categories[0], template.category);
                if (template.amount > 0 && amount.getText().toString().trim().length() == 0) {
                    amount.setText(String.format(Locale.US, "%.2f", template.amount));
                }
                if (template.notes.length() > 0 && notes.getText().toString().trim().length() == 0) {
                    notes.setText(template.notes);
                }
            }
        });

        styleDialogCheckBox(tax, "▣ Flag for tax purpose");
        if (existing != null && existing.taxFlag) tax.setChecked(true);
        tax.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (((CheckBox) view).isChecked()) showTaxDialog(tax, amount, title, taxValues);
                else {
                    taxValues[0] = "";
                    taxValues[1] = "";
                }
            }
        });

        Button addCategory = outlineButton("＋ Add " + entryType.toLowerCase(Locale.US) + " category");
        addCategory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInlineCategoryDialog(entryType, new Runnable() {
                    @Override
                    public void run() {
                        categories[0] = db.categories(selectedCountryId, entryType);
                        resetSpinner(category, categoryLabels(categories[0]));
                        setCategorySpinner(category, categories[0], db.lastCategoryName(selectedCountryId, entryType));
                    }
                });
            }
        });

        final Spinner repeatUnit = spinner(new String[]{"No repeat", "Days", "Weeks", "Months", "Years"});
        final EditText repeatEvery = input("Repeat every", existing == null || existing.repeatInterval <= 0 ? "1" : String.valueOf(existing.repeatInterval), decimalInput());
        final EditText repeatCount = input("Repeat count / term (optional)", existing == null || existing.repeatCount <= 0 ? "" : String.valueOf(existing.repeatCount), decimalInput());
        repeatEvery.setVisibility(View.GONE);
        repeatCount.setVisibility(View.GONE);
        repeatUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                repeatEvery.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
                repeatCount.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        if (existing != null) {
            setCategorySpinner(category, categories[0], existing.category);
            setAccountSelection(account, accounts[0], existing.accountId);
            if (existing.repeatInterval > 0) setSpinnerToValue(repeatUnit, existing.repeatUnit);
        }

        Button bill = outlineButton("▣ Upload bill");
        if (existing != null && existing.billUri.length() > 0) {
            pendingBillUri = existing.billUri;
            bill.setText("▣ Bill attached");
        }
        pendingBillButton = bill;
        bill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, REQUEST_BILL);
            }
        });

        form.addView(dialogSection("Details", title, dialogFieldRow(date, amount)));
        form.addView(label("Category"));
        form.addView(category);
        form.addView(addCategory, matchWrapWithMargins(0, dp(4), 0, 0));
        form.addView(label("Account"));
        form.addView(account);
        form.addView(notes, matchWrapWithMargins(0, dp(10), 0, 0));
        form.addView(tax);
        form.addView(label("Repeating"));
        form.addView(repeatUnit);
        form.addView(repeatEvery);
        form.addView(repeatCount);
        form.addView(bill, matchWrap());

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Save", accent, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                double value = parseDouble(amount.getText().toString());
                if (value <= 0 || title.getText().toString().trim().length() == 0) {
                    toast("Enter a title and amount.");
                    return false;
                }
                Category selectedCategory = categories[0].get(category.getSelectedItemPosition());
                boolean taxFlag = tax.isChecked();
                double taxAmount = taxFlag ? parseDouble(taxValues[0]) : 0;
                String taxTitle = taxFlag ? taxValues[1] : "";
                if (taxFlag && taxAmount <= 0) taxAmount = value;
                if (taxFlag && taxTitle.trim().length() == 0) taxTitle = title.getText().toString().trim();
                int accountPosition = account.getSelectedItemPosition();
                if (accountPosition < 0 || accountPosition >= accounts[0].size()) {
                    toast("Choose or add an account.");
                    return false;
                }
                Account selectedAccount = accounts[0].get(accountPosition);
                int repeatInterval = repeatUnit.getSelectedItemPosition() == 0 ? 0 : Math.max(1, (int) parseDouble(repeatEvery.getText().toString()));
                String repeat = repeatUnit.getSelectedItemPosition() == 0 ? "" : repeatUnit.getSelectedItem().toString();
                int fixedRepeats = repeatUnit.getSelectedItemPosition() == 0 ? 0 : Math.max(0, (int) parseDouble(repeatCount.getText().toString()));

                final String finalDate = clean(date.getText().toString(), today());
                final String finalTitle = clean(title.getText().toString(), "Untitled");
                final String finalCategory = selectedCategory.name;
                final Account finalAccount = selectedAccount;
                final double finalValue = value;
                final boolean finalTaxFlag = taxFlag;
                final double finalTaxAmount = taxAmount;
                final String finalTaxTitle = taxTitle.trim();
                final int finalRepeatInterval = repeatInterval;
                final String finalRepeat = repeat;
                final int finalFixedRepeats = fixedRepeats;
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        if (existing == null) {
                            db.addEntry(selectedCountryId, entryType, finalDate,
                                    finalTitle, finalValue, finalCategory, "",
                                    finalAccount.id, notes.getText().toString().trim(), pendingBillUri,
                                    finalTaxFlag, finalTaxAmount, finalTaxTitle, finalRepeatInterval, finalRepeat, finalFixedRepeats);
                        } else {
                            db.updateEntry(existing.id, entryType, finalDate,
                                    finalTitle, finalValue, finalCategory, "",
                                    finalAccount.id, notes.getText().toString().trim(), pendingBillUri,
                                    finalTaxFlag, finalTaxAmount, finalTaxTitle, finalRepeatInterval, finalRepeat, finalFixedRepeats);
                        }
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                };
                if ("Expense".equals(entryType) && needsOutflowConfirmation(finalAccount, finalValue)) {
                    showBalanceConfirmation("Confirm expense", outflowWarning(finalAccount, finalValue), CORAL, save);
                    return false;
                }
                save.run();
                return true;
            }
        });
    }

    private void showTaxDialog(final CheckBox checkBox, EditText amountInput, EditText titleInput, final String[] taxValues) {
        LinearLayout form = form();
        final Country country = db.getCountry(selectedCountryId);
        final double totalAmount = parseDouble(amountInput.getText().toString());
        addDialogHero(form, "▣", "Tax details", "Specify the amount out of the total to show for tax purpose and the proper report description.", GOLD);
        final LinearLayout taxRows = new LinearLayout(this);
        taxRows.setOrientation(LinearLayout.VERTICAL);
        final List<EditText> taxAmounts = new ArrayList<EditText>();
        final List<EditText> taxTitles = new ArrayList<EditText>();
        addTaxLineRow(taxRows, taxAmounts, taxTitles,
                taxValues[0].length() == 0 ? amountInput.getText().toString() : taxValues[0],
                taxValues[1].length() == 0 ? titleInput.getText().toString() : taxValues[1],
                country);
        Button addLine = outlineButton("＋ Add tax field");
        addLine.setTextColor(GOLD);
        addLine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addTaxLineRow(taxRows, taxAmounts, taxTitles, "", "", country);
            }
        });
        form.addView(dialogSection("Amount to show", taxRows, text("/ " + money(totalAmount, country.currency) + " total", 15, Typeface.BOLD, GOLD), addLine));

        showStyledDialog(form, "Cancel", "Apply", GOLD, new DialogCancel() {
            @Override
            public void cancel() {
                checkBox.setChecked(false);
            }
        }, new DialogSubmit() {
            @Override
            public boolean submit() {
                double sum = 0;
                StringBuilder description = new StringBuilder();
                for (int i = 0; i < taxAmounts.size(); i++) {
                    double lineAmount = parseDouble(taxAmounts.get(i).getText().toString());
                    String lineTitle = clean(taxTitles.get(i).getText().toString(), titleInput.getText().toString());
                    if (lineAmount <= 0) continue;
                    sum += lineAmount;
                    if (description.length() > 0) description.append("; ");
                    description.append(lineTitle).append(" (").append(money(lineAmount, country.currency)).append(")");
                }
                taxValues[0] = moneyPlain(sum);
                taxValues[1] = description.length() == 0 ? titleInput.getText().toString().trim() : description.toString();
                return true;
            }
        });
    }

    private void addTaxLineRow(LinearLayout taxRows, List<EditText> taxAmounts, List<EditText> taxTitles,
                               String amount, String title, Country country) {
        EditText taxAmount = input("Tax amount " + currencySymbol(country.currency), amount, decimalInput());
        EditText taxTitle = input("Tax description", title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        taxRows.addView(dialogFieldRow(taxAmount, taxTitle), matchWrapWithMargins(0, taxAmounts.isEmpty() ? 0 : dp(8), 0, 0));
        taxAmounts.add(taxAmount);
        taxTitles.add(taxTitle);
    }

    private void showBudgetDialog() {
        showBudgetDialog(null);
    }

    private void showBudgetDialog(final BudgetProgress existing) {
        final Country country = db.getCountry(selectedCountryId);
        final List<Category> categories = db.categories(selectedCountryId, "Expense");
        LinearLayout form = form();
        final List<CheckBox> categoryChecks = new ArrayList<CheckBox>();
        final EditText name = input("Budget name", existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final EditText month = input("Month (YYYY-MM)", existing == null ? monthToday() : existing.month, InputType.TYPE_CLASS_DATETIME);
        final EditText limit = input("Monthly limit in " + currencySymbol(country.currency), existing == null ? "" : moneyPlain(existing.limit), decimalInput());
        final EditText notes = multiInput("Notes");
        if (existing != null) notes.setText(existing.notes);
        final CheckBox recurring = new CheckBox(this);
        styleDialogCheckBox(recurring, "Repeat this budget every month");
        recurring.setChecked(existing == null || existing.recurring);
        addDialogHero(form, "▦", existing == null ? "Add budget" : "Edit budget", "Single-category budgets use the category name automatically.", BLUE);
        form.addView(dialogSection("Budget details", name, dialogFieldRow(month, limit)));
        form.addView(label("Budget categories"));
        for (Category item : categories) {
            CheckBox check = new CheckBox(this);
            styleDialogCheckBox(check, item.icon + " " + item.name);
            if (existing != null && categoryListContains(existing.category, item.name)) check.setChecked(true);
            categoryChecks.add(check);
            form.addView(check);
        }
        updateBudgetNameVisibility(name, categories, categoryChecks);
        for (CheckBox check : categoryChecks) {
            check.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    updateBudgetNameVisibility(name, categories, categoryChecks);
                }
            });
        }
        form.addView(recurring);
        form.addView(notes);

        showStyledDialog(form, "Cancel", "Save", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                double limitValue = parseDouble(limit.getText().toString());
                if (limitValue <= 0) {
                    toast("Enter a budget limit.");
                    return false;
                }
                String selectedCategories = checkedCategoryNames(categories, categoryChecks);
                if (selectedCategories.length() == 0) {
                    toast("Choose at least one expense category.");
                    return false;
                }
                String budgetName = checkedCategoryCount(categoryChecks) > 1
                        ? clean(name.getText().toString(), "Monthly budget")
                        : selectedCategories;
                if (existing == null) {
                    db.addBudget(selectedCountryId, budgetName,
                            selectedCategories, "", clean(month.getText().toString(), monthToday()),
                            limitValue, notes.getText().toString().trim(), recurring.isChecked());
                } else {
                    db.updateBudget(existing.id, budgetName, selectedCategories, "",
                            clean(month.getText().toString(), monthToday()), limitValue,
                            notes.getText().toString().trim(), recurring.isChecked());
                }
                render();
                return true;
            }
        });
    }

    private void showInvestmentDialog() {
        showInvestmentDialog(null);
    }

    private void showInvestmentDialog(final InvestmentDraft draft) {
        final Country country = db.getCountry(selectedCountryId);
        final List<Account> accounts = db.accounts(selectedCountryId);
        if (accounts.isEmpty()) {
            toast("Add an account before opening an investment.");
            return;
        }
        LinearLayout form = form();
        final Spinner type = spinner(new String[]{"Fixed Deposit", "Recurring Deposit", "Market Linked Fund"});
        final EditText title = input("Title", draft == null ? "" : draft.title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final Spinner account = spinner(accountNames(accounts));
        final EditText principal = input("Principal amount in " + currencySymbol(country.currency),
                draft == null ? "" : moneyPlain(draft.principal), decimalInput());
        final EditText start = dateInput("Date of opening", draft == null ? today() : draft.startDate, false);
        final EditText maturity = dateInput("Date of maturity", draft == null ? "" : draft.maturityDate, true);
        final EditText maturityValue = input("Value at maturity",
                draft == null ? "" : moneyPlain(draft.maturityValue), decimalInput());
        final Spinner maturityAction = spinner(new String[]{"Return full maturity amount to linked account", "Reinvest principal only", "Reinvest principal plus interest"});
        final CheckBox recurring = new CheckBox(this);
        styleDialogCheckBox(recurring, "Repeating investment contribution");
        final Spinner repeatFrequency = spinner(new String[]{"Same day every month", "Same day every year"});
        final EditText notes = multiInput("Notes");

        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                String selected = type.getSelectedItem().toString();
                boolean market = "Market Linked Fund".equals(selected);
                boolean repeatable = "Recurring Deposit".equals(selected) || market;
                maturityAction.setVisibility(market ? View.GONE : View.VISIBLE);
                recurring.setVisibility(repeatable ? View.VISIBLE : View.GONE);
                repeatFrequency.setVisibility(repeatable && recurring.isChecked() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        recurring.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                repeatFrequency.setVisibility(recurring.isChecked() ? View.VISIBLE : View.GONE);
            }
        });
        recurring.setVisibility(View.GONE);
        repeatFrequency.setVisibility(View.GONE);
        if (draft != null) {
            setSpinnerToValue(type, draft.type);
            setAccountSelection(account, accounts, draft.accountId);
            if (draft.maturityAction.length() > 0) setSpinnerToValue(maturityAction, draft.maturityAction);
            if (draft.notes.length() > 0) notes.setText(draft.notes);
        }

        addDialogHero(form, "↗", draft == null ? "Add investment" : "Reinvest maturity",
                draft == null ? "Track principal, maturity and how money should return."
                        : "Review the pre-filled values, then save the new investment.", BLUE);
        form.addView(label("Investment type"));
        form.addView(type);
        form.addView(dialogSection("Investment details", title, principal));
        form.addView(label("Linked account"));
        form.addView(account);
        form.addView(dialogSection("Dates and maturity", dialogFieldRow(start, maturity), maturityValue));
        form.addView(label("Instructions"));
        form.addView(maturityAction);
        form.addView(recurring);
        form.addView(repeatFrequency);
        form.addView(notes);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Save", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                double principalValue = parseDouble(principal.getText().toString());
                if (title.getText().toString().trim().length() == 0 || principalValue <= 0) {
                    toast("Enter a title and principal amount.");
                    return false;
                }
                final Account selectedAccount = accounts.get(account.getSelectedItemPosition());
                final long accountId = selectedAccount.id;
                final double presentValue = principalValue;
                double maturityAmount = parseDouble(maturityValue.getText().toString());
                if (maturityAmount <= 0) maturityAmount = presentValue;
                final double finalMaturityAmount = maturityAmount;
                final double finalPrincipalValue = principalValue;
                final boolean reinvestedDraft = draft != null && draft.reinvested;
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        String storedNotes = investmentNotes(notes.getText().toString().trim(), recurring, repeatFrequency);
                        if (reinvestedDraft && !storedNotes.contains("Created from maturity reinvestment")) {
                            storedNotes = storedNotes.length() == 0
                                    ? "Created from maturity reinvestment"
                                    : storedNotes + "\nCreated from maturity reinvestment";
                        }
                        db.addInvestment(selectedCountryId, type.getSelectedItem().toString(),
                                clean(title.getText().toString(), "Investment"), accountId, finalPrincipalValue, presentValue, finalMaturityAmount,
                                clean(start.getText().toString(), today()), maturity.getText().toString().trim(),
                                "Market Linked Fund".equals(type.getSelectedItem().toString()) ? "" : maturityAction.getSelectedItem().toString(),
                                storedNotes, !reinvestedDraft);
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                };
                if (!reinvestedDraft && needsInvestmentBalanceConfirmation(selectedAccount, finalPrincipalValue)) {
                    showBalanceConfirmation("Confirm investment",
                            investmentBalanceWarning(selectedAccount, finalPrincipalValue), BLUE, save);
                    return false;
                }
                save.run();
                return true;
            }
        });
    }

    private void showCapitalGainsDialog(final Investment investment) {
        final Country country = db.getCountry(selectedCountryId);
        LinearLayout form = form();
        final EditText date = dateInput("Update date", today(), false);
        final EditText presentValue = input("New present value", moneyPlain(investment.currentValue), decimalInput());
        final EditText gains = input("Capital gains / income amount", "", decimalInput());
        addDialogHero(form, "↗", "Update investment", "Add a present value update and optional income entry.", PURPLE);
        form.addView(dialogSection("Value update", dialogFieldRow(date, presentValue), gains));

        showStyledDialog(form, "Cancel", "Save", PURPLE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                double newValue = parseDouble(presentValue.getText().toString());
                double gain = parseDouble(gains.getText().toString());
                if (newValue <= 0) {
                    toast("Enter the present value.");
                    return false;
                }
                db.updateInvestmentValueWithGain(investment, selectedCountryId, clean(date.getText().toString(), today()), newValue, gain);
                if (gain > 0) toast("Capital gains added to income.");
                render();
                return true;
            }
        });
    }

    private void showEditInvestmentAmountDialog(final Investment investment) {
        LinearLayout form = form();
        final EditText principal = input("Invested principal", moneyPlain(investment.principal), decimalInput());
        final EditText present = input("Present value", moneyPlain(investment.currentValue), decimalInput());
        final EditText maturity = input("Maturity value", moneyPlain(investment.maturityValue), decimalInput());
        addDialogHero(form, "↗", "Edit amount", "Update the investment amount without adding a capital gains entry.", BLUE);
        form.addView(dialogSection("Amounts", principal, dialogFieldRow(present, maturity)));
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Save", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                double principalValue = parseDouble(principal.getText().toString());
                double presentValue = parseDouble(present.getText().toString());
                double maturityValue = parseDouble(maturity.getText().toString());
                if (principalValue <= 0) {
                    toast("Enter invested principal.");
                    return false;
                }
                if (presentValue <= 0) presentValue = principalValue;
                if (maturityValue <= 0) maturityValue = presentValue;
                db.updateInvestmentAmounts(investment, principalValue, presentValue, maturityValue);
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                render();
                return true;
            }
        });
    }

    private void showMarketFundMaturityDialog(final Investment investment) {
        final List<Account> accounts = db.accounts(selectedCountryId);
        if (accounts.isEmpty()) {
            toast("Add an account before processing maturity.");
            return;
        }
        LinearLayout form = form();
        final EditText date = dateInput("Transfer date", today(), false);
        final EditText amount = input("Money received", moneyPlain(Math.max(investment.currentValue, investment.maturityValue)), decimalInput());
        final Spinner account = spinner(accountNames(accounts));
        setAccountSelection(account, accounts, investment.accountId);
        addDialogHero(form, "↗", "Redeem market fund", "Enter the amount received and the account where money is transferred.", BLUE);
        form.addView(dialogSection("Redemption", dialogFieldRow(date, amount), account));
        showStyledDialog(form, "Cancel", "Apply", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                double value = parseDouble(amount.getText().toString());
                if (value <= 0) {
                    toast("Enter money received.");
                    return false;
                }
                Account selectedAccount = accounts.get(account.getSelectedItemPosition());
                db.processMarketFundMaturity(investment, selectedCountryId, clean(date.getText().toString(), today()), value, selectedAccount.id);
                render();
                return true;
            }
        });
    }

    private void showAddInvestmentFundsDialog(final Investment investment) {
        if ("Fixed Deposit".equals(investment.type)) {
            toast("Additional principal is available for RDs and market linked funds.");
            return;
        }
        final Account linkedAccount = db.account(investment.accountId);
        if (linkedAccount.id == 0) {
            toast("This investment needs a linked account.");
            return;
        }
        LinearLayout form = form();
        final EditText date = dateInput("Contribution date", today(), false);
        final EditText amount = input("Additional principal", "", decimalInput());
        final EditText maturityValue = input("Maturity value after adding (optional)", "", decimalInput());
        final EditText notes = multiInput("Notes");
        addDialogHero(form, "↗", "Add funds", "Increase principal and adjust the expected maturity value.", BLUE);
        form.addView(dialogSection(investment.type, dialogFieldRow(date, amount), maturityValue));
        form.addView(notes);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Save", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                final double additional = parseDouble(amount.getText().toString());
                if (additional <= 0) {
                    toast("Enter the additional principal.");
                    return false;
                }
                double nextMaturity = parseDouble(maturityValue.getText().toString());
                if (nextMaturity <= 0) nextMaturity = investment.maturityValue + additional;
                final double finalMaturity = nextMaturity;
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        db.addInvestmentFunds(investment, selectedCountryId, clean(date.getText().toString(), today()),
                                additional, finalMaturity, notes.getText().toString().trim());
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                };
                if (needsInvestmentBalanceConfirmation(linkedAccount, additional)) {
                    showBalanceConfirmation("Confirm added funds",
                            investmentBalanceWarning(linkedAccount, additional), BLUE, save);
                    return false;
                }
                save.run();
                return true;
            }
        });
    }

    private void showMaturityDialog(final Investment investment) {
        LinearLayout form = form();
        final Spinner closeTiming = spinner(new String[]{"On maturity", "Before maturity"});
        final EditText date = dateInput("Processing date", investment.maturityDate.length() == 0 ? today() : investment.maturityDate, false);
        final EditText maturityValue = input("Maturity value / money returned", moneyPlain(investment.maturityValue), decimalInput());
        final Spinner action = spinner(new String[]{"Return full maturity amount to linked account", "Reinvest principal only", "Reinvest principal plus interest"});
        setSpinnerToValue(action, investment.maturityAction);
        closeTiming.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                action.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        addDialogHero(form, "↗", "Close investment", "Choose whether this is closing on maturity or before maturity.", BLUE);
        form.addView(dialogSection("Close type", closeTiming));
        form.addView(dialogSection("Amount", dialogFieldRow(date, maturityValue), text("Linked account: " + investment.accountName, 13, Typeface.BOLD, BLUE)));
        form.addView(label("Instructions"));
        form.addView(action);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Apply", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                double value = parseDouble(maturityValue.getText().toString());
                if (value <= 0) {
                    toast(closeTiming.getSelectedItemPosition() == 0 ? "Enter maturity value." : "Enter money returned.");
                    return false;
                }
                String finalDate = clean(date.getText().toString(), today());
                if (closeTiming.getSelectedItemPosition() == 1) {
                    db.processEarlyInvestmentClose(investment, finalDate, value);
                    render();
                    return true;
                }
                String instruction = action.getSelectedItem().toString();
                if ("Return full maturity amount to linked account".equals(instruction)) {
                    db.processMaturity(investment, finalDate, value, instruction);
                    render();
                    return true;
                }
                double reinvestAmount = "Reinvest principal only".equals(instruction)
                        ? Math.min(investment.principal, value) : value;
                double returnedAmount = Math.max(0, value - reinvestAmount);
                if (reinvestAmount <= 0) {
                    toast("Enter an amount to reinvest.");
                    return false;
                }
                db.processMaturityForReinvestmentChoice(investment, finalDate, value, instruction, returnedAmount);
                InvestmentDraft draft = new InvestmentDraft();
                draft.type = investment.type;
                draft.title = investment.title + " reinvested";
                draft.accountId = investment.accountId;
                draft.principal = reinvestAmount;
                draft.startDate = finalDate;
                draft.maturityValue = reinvestAmount;
                draft.maturityAction = instruction;
                draft.reinvested = true;
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                render();
                showInvestmentDialog(draft);
                return true;
            }
        });
    }

    private void showAccountDialog() {
        showAccountDialog(null, null);
    }

    private void showAccountDialog(final Runnable afterSave) {
        showAccountDialog(null, afterSave);
    }

    private void showAccountDialog(final Account existing) {
        showAccountDialog(existing, null);
    }

    private void showAccountDialog(final Account existing, final Runnable afterSave) {
        final Country country = db.getCountry(selectedCountryId);
        LinearLayout form = form();
        final EditText name = input("Account name", existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final Spinner type = spinner(new String[]{"Bank", "Cash", "Credit Card", "Loan", "Wallet", "Brokerage", "Other"});
        if (existing != null) setSpinnerToValue(type, existing.type);
        final EditText balance = input(existing == null ? "Starting balance / current amount" : "Current balance",
                existing == null ? "" : moneyPlain(existing.balance), signedDecimalInput());
        final AutoCompleteTextView currency = currencySearchInput(existing == null ? country.currency : existing.currency);
        addDialogHero(form, "▣", existing == null ? "Add account" : "Edit account", "Create a place where your money actually sits.", BLUE);
        form.addView(dialogSection("Account details", name, dialogFieldRow(balance, type), currency));

        showStyledDialog(form, "Cancel", "Save", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                if (name.getText().toString().trim().length() == 0) {
                    toast("Enter an account name.");
                    return false;
                }
                if (existing == null) {
                    db.addAccount(selectedCountryId, name.getText().toString().trim(),
                            type.getSelectedItem().toString(), parseDouble(balance.getText().toString()),
                            selectedCurrencyCode(currency));
                } else {
                    db.updateAccount(existing.id, name.getText().toString().trim(),
                            type.getSelectedItem().toString(), parseDouble(balance.getText().toString()),
                            selectedCurrencyCode(currency));
                }
                selectedAccountIds.clear();
                if (afterSave != null) afterSave.run();
                else render();
                return true;
            }
        });
    }

    private void showGoalDialog() {
        showGoalDialog(null);
    }

    private void showGoalDialog(final Goal existing) {
        final Country country = db.getCountry(selectedCountryId);
        final List<Account> accounts = db.accounts(selectedCountryId);
        if (accounts.isEmpty()) {
            toast("Add an account before creating a goal.");
            return;
        }
        LinearLayout form = form();
        final EditText title = input("Goal title", existing == null ? "" : existing.title, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final Spinner account = spinner(accountNames(accounts));
        if (existing != null) setAccountSelection(account, accounts, existing.accountId);
        final EditText target = input("Target amount in " + currencySymbol(country.currency), existing == null ? "" : moneyPlain(existing.targetAmount), decimalInput());
        final EditText current = input("Already set aside", existing == null ? "0" : moneyPlain(existing.currentAmount), decimalInput());
        final EditText date = dateInput("Target date (optional)", existing == null ? "" : existing.targetDate, true);
        final EditText notes = multiInput("Notes");
        if (existing != null) notes.setText(existing.notes);
        addDialogHero(form, "◎", existing == null ? "Add goal" : "Edit goal", "Reserve money inside a real account for something specific.", TEAL);
        form.addView(dialogSection("Goal details", title, dialogFieldRow(target, current), dialogFieldRow(date, account)));
        form.addView(label("Set aside in account"));
        form.addView(notes);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Save", TEAL, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                if (title.getText().toString().trim().length() == 0 || parseDouble(target.getText().toString()) <= 0) {
                    toast("Enter a title and target amount.");
                    return false;
                }
                final Account selectedAccount = accounts.get(account.getSelectedItemPosition());
                final double targetAmount = parseDouble(target.getText().toString());
                final double currentAmount = parseDouble(current.getText().toString());
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        if (existing == null) {
                            db.addGoal(selectedCountryId, title.getText().toString().trim(),
                                    targetAmount, currentAmount,
                                    date.getText().toString().trim(), selectedAccount.id, notes.getText().toString().trim());
                        } else {
                            db.updateGoal(existing.id, title.getText().toString().trim(),
                                    targetAmount, currentAmount,
                                    date.getText().toString().trim(), selectedAccount.id, notes.getText().toString().trim());
                        }
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                };
                if (denyGoalIfUnavailable(selectedAccount, currentAmount, existing == null ? 0 : existing.id)) {
                    return false;
                }
                save.run();
                return true;
            }
        });
    }

    private void showGoalContributionDialog(final Goal goal) {
        final List<Account> accounts = db.accounts(selectedCountryId);
        final EditText amount = input("Add amount", "", decimalInput());
        LinearLayout form = form();
        addDialogHero(form, "◎", "Update goal", "Increase the amount reserved for this goal.", TEAL);
        form.addView(dialogSection("Progress", amount));
        final Spinner account = spinner(accountNames(accounts));
        if (goal.accountId == 0 && !accounts.isEmpty()) {
            form.addView(dialogSection("Set aside in account", account));
        }
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Apply", TEAL, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                final double nextAmount = goal.currentAmount + parseDouble(amount.getText().toString());
                long accountId = goal.accountId;
                if (accountId == 0 && !accounts.isEmpty()) accountId = accounts.get(account.getSelectedItemPosition()).id;
                if (accountId == 0) {
                    toast("Choose an account for this goal.");
                    return false;
                }
                final long finalAccountId = accountId;
                final Account selectedAccount = db.account(finalAccountId);
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        db.updateGoalCurrent(goal.id, nextAmount, finalAccountId);
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                };
                if (denyGoalIfUnavailable(selectedAccount, nextAmount, goal.id)) {
                    return false;
                }
                save.run();
                return true;
            }
        });
    }

    private void showGoalAccountDialog(final Goal goal) {
        final List<Account> accounts = db.accounts(selectedCountryId);
        if (accounts.isEmpty()) {
            toast("Add an account before linking this goal.");
            return;
        }
        final Spinner account = spinner(accountNames(accounts));
        LinearLayout form = form();
        addDialogHero(form, "◎", "Set goal account", "Choose where this goal money is being held.", TEAL);
        form.addView(dialogSection("Account", account));
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Save", TEAL, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                final Account selectedAccount = accounts.get(account.getSelectedItemPosition());
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        db.updateGoalCurrent(goal.id, goal.currentAmount, selectedAccount.id);
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                };
                if (denyGoalIfUnavailable(selectedAccount, goal.currentAmount, goal.id)) {
                    return false;
                }
                save.run();
                return true;
            }
        });
    }

    private boolean needsGoalBalanceConfirmation(Account account, double goalAmount, long excludingGoalId) {
        if (account == null || account.id == 0 || goalAmount <= 0) return false;
        double otherReserved = db.goalReservedForAccountExcept(account.id, excludingGoalId);
        return otherReserved + goalAmount > account.balance;
    }

    private boolean denyGoalIfUnavailable(Account account, double goalAmount, long excludingGoalId) {
        if (!needsGoalBalanceConfirmation(account, goalAmount, excludingGoalId)) return false;
        double available = account.balance - db.goalReservedForAccountExcept(account.id, excludingGoalId);
        LinearLayout form = form();
        addDialogHero(form, "!", "Balance is low", "Available: " + money(available, account.currency)
                + ". Want to set aside: " + money(goalAmount, account.currency)
                + ". Reduce the goal amount or choose another account.", CORAL);
        showStyledDialog(form, null, "OK", CORAL, null, null);
        return true;
    }

    private String goalBalanceWarning(Account account, double goalAmount, long excludingGoalId) {
        double otherReserved = db.goalReservedForAccountExcept(account.id, excludingGoalId);
        return "This will set aside " + money(goalAmount, account.currency) + " for this goal in " + account.name
                + ". Balance is low: " + money(account.balance - otherReserved, account.currency)
                + ". Want to set aside " + money(goalAmount, account.currency) + "?";
    }

    private boolean needsInvestmentBalanceConfirmation(Account account, double amount) {
        if (account == null || account.id == 0 || amount <= 0) return false;
        return amount > availableForInvestment(account);
    }

    private String investmentBalanceWarning(Account account, double amount) {
        return outflowWarning(account, amount);
    }

    private String investmentNotes(String notes, CheckBox recurring, Spinner frequency) {
        if (recurring.getVisibility() != View.VISIBLE || !recurring.isChecked()) return notes;
        String repeat = "Repeats: " + frequency.getSelectedItem().toString();
        return notes.length() == 0 ? repeat : notes + "\n" + repeat;
    }

    private double availableForInvestment(Account account) {
        return account.balance - db.goalReservedForAccount(account.id);
    }

    private boolean needsOutflowConfirmation(Account account, double amount) {
        if (account == null || account.id == 0 || amount <= 0) return false;
        return amount > availableForInvestment(account) || amount > account.balance;
    }

    private String outflowWarning(Account account, double amount) {
        double reserved = db.goalReservedForAccount(account.id);
        double available = account.balance - reserved;
        double after = account.balance - amount;
        if (after < 0) {
            return "This will make " + account.name + " negative. Balance after: " + money(after, account.currency) + ". Confirm?";
        }
        return "This will use money set aside for goals. Available: " + money(available, account.currency)
                + ". Amount: " + money(amount, account.currency) + ". Confirm?";
    }

    private boolean sourceEntryEditable(Entry entry) {
        String source = safeText(entry.sourceType);
        return source.length() == 0 || SOURCE_INVESTMENT_MATURITY.equals(source);
    }

    private void showBalanceConfirmation(String title, String message, int accent, final Runnable action) {
        LinearLayout form = form();
        addDialogHero(form, "!", title, message, accent);
        showStyledDialog(form, "Cancel", "Confirm", accent, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                action.run();
                return true;
            }
        });
    }

    private void showTransferDialog() {
        showTransferDialog(null);
    }

    private void showTransferDialog(final Transfer existing) {
        final List<Country> countries = db.getCountries();
        if (countries.size() < 2) {
            toast("Add at least two currency accounts before recording transfers.");
            return;
        }
        final Transfer preset = existing == null ? db.latestTransfer() : existing;
        LinearLayout form = form();
        final Spinner fromCountry = spinner(countryNames(countries));
        final Spinner toCountry = spinner(countryNames(countries));
        if (preset != null) {
            fromCountry.setSelection(countryIndex(countries, preset.fromCountryId));
            toCountry.setSelection(countryIndex(countries, preset.toCountryId));
        } else if (countries.size() > 1) {
            toCountry.setSelection(1);
        }
        final Spinner fromAccount = spinner(accountNames(db.accounts(countries.get(fromCountry.getSelectedItemPosition()).id)));
        final Spinner toAccount = spinner(accountNames(db.accounts(countries.get(toCountry.getSelectedItemPosition()).id)));
        if (preset != null) {
            setAccountSelection(fromAccount, db.accounts(preset.fromCountryId), preset.fromAccountId);
            setAccountSelection(toAccount, db.accounts(preset.toCountryId), preset.toAccountId);
        }
        final EditText date = dateInput("Transfer date", existing == null ? today() : existing.date, false);
        final EditText amount = input("Transfer amount", preset == null ? "" : moneyPlain(preset.fromAmount), decimalInput());
        final EditText fee = input("Transfer fee", preset == null ? "0" : moneyPlain(preset.feeAmount), decimalInput());
        final RadioGroup feeMode = feeModeRadioGroup();
        final EditText rate = input("Conversion rate", preset == null ? "" : oneFour(preset.rate), decimalInput());
        final TextView converted = text("Converted amount will appear here.", 13, Typeface.BOLD, TEAL);
        final EditText notes = multiInput("Notes");
        if (existing != null) notes.setText(existing.notes);
        final boolean[] initializingTransfer = new boolean[]{existing != null};

        AdapterView.OnItemSelectedListener countryListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                if (initializingTransfer[0]) return;
                resetSpinner(fromAccount, accountNames(db.accounts(countries.get(fromCountry.getSelectedItemPosition()).id)));
                resetSpinner(toAccount, accountNames(db.accounts(countries.get(toCountry.getSelectedItemPosition()).id)));
                applyDailyRate(countries, fromCountry, toCountry, date, rate, amount, fee, feeMode, converted);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        };
        fromCountry.setOnItemSelectedListener(countryListener);
        toCountry.setOnItemSelectedListener(countryListener);
        initializingTransfer[0] = false;
        if (preset != null) {
            fromCountry.post(new Runnable() {
                @Override
                public void run() {
                    resetSpinner(fromAccount, accountNames(db.accounts(preset.fromCountryId)));
                    resetSpinner(toAccount, accountNames(db.accounts(preset.toCountryId)));
                    setAccountSelection(fromAccount, db.accounts(preset.fromCountryId), preset.fromAccountId);
                    setAccountSelection(toAccount, db.accounts(preset.toCountryId), preset.toAccountId);
                    rate.setText(oneFour(preset.rate));
                    updateConverted(rate, amount, fee, feeMode, converted, preset.fromCurrency, preset.toCurrency);
                }
            });
        }
        amount.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) updateConverted(rate, amount, fee, feeMode, converted,
                        countries.get(fromCountry.getSelectedItemPosition()).currency,
                        countries.get(toCountry.getSelectedItemPosition()).currency);
            }
        });
        fee.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) updateConverted(rate, amount, fee, feeMode, converted,
                        countries.get(fromCountry.getSelectedItemPosition()).currency,
                        countries.get(toCountry.getSelectedItemPosition()).currency);
            }
        });
        rate.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) updateConverted(rate, amount, fee, feeMode, converted,
                        countries.get(fromCountry.getSelectedItemPosition()).currency,
                        countries.get(toCountry.getSelectedItemPosition()).currency);
            }
        });
        feeMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                updateConverted(rate, amount, fee, feeMode, converted,
                        countries.get(fromCountry.getSelectedItemPosition()).currency,
                        countries.get(toCountry.getSelectedItemPosition()).currency);
            }
        });
        if (existing == null && preset == null) applyDailyRate(countries, fromCountry, toCountry, date, rate, amount, fee, feeMode, converted);
        else updateConverted(rate, amount, fee, feeMode, converted,
                countries.get(fromCountry.getSelectedItemPosition()).currency,
                countries.get(toCountry.getSelectedItemPosition()).currency);

        addDialogHero(form, "⇄", existing == null ? "Send money" : "Edit transfer",
                "Record the transfer, fee and conversion rate together.", SKY);
        form.addView(dialogSection("From", fromCountry, fromAccount));
        form.addView(dialogSection("To", toCountry, toAccount));
        form.addView(dialogSection("Transfer details", date, dialogFieldRow(amount, fee), feeMode, rate));
        Button useDaily = outlineButton("↻ Use daily rate");
        useDaily.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                applyDailyRate(countries, fromCountry, toCountry, date, rate, amount, fee, feeMode, converted);
                if (parseDouble(rate.getText().toString()) <= 0) {
                    fetchDailyRateIntoInput(countries.get(fromCountry.getSelectedItemPosition()).currency,
                            countries.get(toCountry.getSelectedItemPosition()).currency,
                            clean(date.getText().toString(), today()), rate, amount, fee, feeMode, converted);
                }
            }
        });
        form.addView(useDaily, matchWrapWithMargins(0, dp(6), 0, 0));
        form.addView(converted);
        form.addView(notes);

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        dialogHolder[0] = showStyledDialog(form, "Cancel", "Save", SKY, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                Country from = countries.get(fromCountry.getSelectedItemPosition());
                Country to = countries.get(toCountry.getSelectedItemPosition());
                List<Account> fromAccounts = db.accounts(from.id);
                List<Account> toAccounts = db.accounts(to.id);
                if (fromAccounts.isEmpty() || toAccounts.isEmpty()) {
                    toast("Both currency accounts need a wallet/account.");
                    return false;
                }
                double sourceAmount = parseDouble(amount.getText().toString());
                double feeValue = parseDouble(fee.getText().toString());
                double rateValue = parseDouble(rate.getText().toString());
                if (sourceAmount <= 0 || rateValue <= 0) {
                    toast("Enter amount and conversion rate.");
                    return false;
                }
                boolean inclusiveFee = isFeeInclusive(feeMode);
                double transferAmount = inclusiveFee ? sourceAmount - Math.max(0, feeValue) : sourceAmount;
                if (transferAmount <= 0) {
                    toast("Transfer amount must be more than the inclusive fee.");
                    return false;
                }
                Account source = fromAccounts.get(Math.min(fromAccount.getSelectedItemPosition(), fromAccounts.size() - 1));
                Account destination = toAccounts.get(Math.min(toAccount.getSelectedItemPosition(), toAccounts.size() - 1));
                double destinationAmount = transferAmount * rateValue;
                final double debitAmount = transferAmount + Math.max(0, feeValue);
                final Account riskAccount = db.account(source.id);
                if (existing != null && existing.fromAccountId == source.id) {
                    riskAccount.balance += existing.fromAmount + Math.max(0, existing.feeAmount);
                }
                final String finalDate = clean(date.getText().toString(), today());
                final Country finalFrom = from;
                final Country finalTo = to;
                final Account finalSource = source;
                final Account finalDestination = destination;
                final double finalTransferAmount = transferAmount;
                final double finalDestinationAmount = destinationAmount;
                final double finalRateValue = rateValue;
                final double finalFeeValue = feeValue;
                final String finalNotes = notes.getText().toString().trim();
                final Runnable save = new Runnable() {
                    @Override
                    public void run() {
                        if (existing == null) {
                            db.addTransfer(finalDate, finalFrom.id, finalTo.id,
                                    finalSource.id, finalDestination.id, finalTransferAmount, finalDestinationAmount, finalRateValue, finalFeeValue,
                                    db.rateSource(finalFrom.currency, finalTo.currency, finalDate),
                                    finalNotes);
                        } else {
                            db.updateTransfer(existing.id, finalDate, finalFrom.id, finalTo.id,
                                    finalSource.id, finalDestination.id, finalTransferAmount, finalDestinationAmount, finalRateValue, finalFeeValue,
                                    db.rateSource(finalFrom.currency, finalTo.currency, finalDate),
                                    finalNotes);
                        }
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        render();
                    }
                };
                if (needsOutflowConfirmation(riskAccount, debitAmount)) {
                    showBalanceConfirmation("Confirm transfer", outflowWarning(riskAccount, debitAmount), SKY, save);
                    return false;
                }
                save.run();
                return true;
            }
        });
    }

    private void showRatesDialog() {
        final List<RateView> rates = db.rateViewsForCountries(today());
        if (rates.isEmpty()) {
            toast("Add at least two currency accounts first.");
            return;
        }
        LinearLayout form = form();
        final EditText date = dateInput("Rate date", today(), false);
        final List<EditText> inputs = new ArrayList<EditText>();
        addDialogHero(form, "⇄", "Update daily rates", "Edit the rates used for transfers between currency accounts.", SKY);
        form.addView(dialogSection("Rate date", date));
        for (RateView rate : rates) {
            form.addView(label(rate.fromCurrency + " to " + rate.toCurrency + " for today"));
            EditText input = input("Rate", rate.rate > 0 ? oneFour(rate.rate) : "", decimalInput());
            inputs.add(input);
            form.addView(input);
        }
        Button fetch = outlineButton("↻ Fetch daily rates");
        fetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fetchDailyRates(clean(date.getText().toString(), today()), new RateFetchComplete() {
                    @Override
                    public void complete(int saved) {
                        if (saved <= 0) return;
                        String rateDate = clean(date.getText().toString(), today());
                        for (int index = 0; index < rates.size() && index < inputs.size(); index++) {
                            double value = db.dailyRate(rates.get(index).fromCurrency, rates.get(index).toCurrency, rateDate);
                            inputs.get(index).setText(value > 0 ? oneFour(value) : "");
                        }
                    }
                });
            }
        });
        form.addView(fetch, matchWrapWithMargins(0, dp(8), 0, 0));

        showStyledDialog(form, "Cancel", "Save", SKY, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                for (int index = 0; index < rates.size(); index++) {
                    double value = parseDouble(inputs.get(index).getText().toString());
                    if (value > 0) {
                        String rateDate = clean(date.getText().toString(), today());
                        db.upsertRate(rateDate, rates.get(index).fromCurrency, rates.get(index).toCurrency, value, "Edited daily rate");
                        db.upsertRate(rateDate, rates.get(index).toCurrency, rates.get(index).fromCurrency, 1.0 / value, "Edited daily rate");
                    }
                }
                render();
                return true;
            }
        });
    }

    private void fetchDailyRates(final String date) {
        fetchDailyRates(date, null);
    }

    private void fetchDailyRates(final String date, final RateFetchComplete afterComplete) {
        final List<RateView> rates = db.rateViewsForCountries(date);
        if (rates.isEmpty()) {
            toast("Add at least two currency accounts first.");
            return;
        }
        final RateFetchUi progress = showRateFetchDialog("Fetching daily rates",
                "Getting rates for " + date + ".", rates.size(), SKY);
        final boolean[] finished = {false};
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!finished[0] && progress.dialog != null && progress.dialog.isShowing()) {
                    progress.status.setText("Unable to fetch yet");
                    progress.detail.setText("This is taking longer than expected. Please feed manually, or wait a little longer.");
                }
            }
        }, 8000);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int saved = 0;
                for (int index = 0; index < rates.size(); index++) {
                    RateView rate = rates.get(index);
                    double value = fetchProviderRate(rate.fromCurrency, rate.toCurrency, date);
                    if (value > 0) {
                        db.upsertRate(date, rate.fromCurrency, rate.toCurrency, value, "Frankfurter daily rate");
                        db.upsertRate(date, rate.toCurrency, rate.fromCurrency, 1.0 / value, "Frankfurter daily rate");
                        saved++;
                    }
                    final int done = index + 1;
                    final int savedSoFar = saved;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateRateFetchProgress(progress, done, rates.size(),
                                    savedSoFar + " rate" + (savedSoFar == 1 ? "" : "s") + " updated so far.");
                        }
                    });
                }
                final int count = saved;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finished[0] = true;
                        if (count > 0) {
                            updateRateFetchProgress(progress, rates.size(), rates.size(),
                                    "Daily rates updated. You can still edit them manually.");
                            if (afterComplete != null) afterComplete.complete(count);
                            progress.status.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (progress.dialog != null && progress.dialog.isShowing()) progress.dialog.dismiss();
                                    if (afterComplete == null) render();
                                }
                            }, 700);
                        } else {
                            progress.status.setText("Unable to fetch");
                            progress.detail.setText("Please feed manually.");
                        }
                    }
                });
            }
        }).start();
    }

    private void fetchDailyRateIntoInput(final String fromCurrency, final String toCurrency, final String date,
                                         final EditText rateInput, final EditText amountInput, final EditText feeInput,
                                         final RadioGroup feeMode, final TextView converted) {
        final RateFetchUi progress = showRateFetchDialog("Fetching daily rate",
                fromCurrency + " to " + toCurrency + " for " + date + ".", 1, SKY);
        final boolean[] finished = {false};
        root.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!finished[0] && progress.dialog != null && progress.dialog.isShowing()) {
                    progress.status.setText("Unable to fetch yet");
                    progress.detail.setText("This is taking longer than expected. Please feed manually, or wait a little longer.");
                }
            }
        }, 8000);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final double value = fetchProviderRate(fromCurrency, toCurrency, date);
                if (value > 0) {
                    db.upsertRate(date, fromCurrency, toCurrency, value, "Frankfurter daily rate");
                    db.upsertRate(date, toCurrency, fromCurrency, 1.0 / value, "Frankfurter daily rate");
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finished[0] = true;
                        if (value > 0) {
                            updateRateFetchProgress(progress, 1, 1, "Daily rate updated.");
                            rateInput.setText(oneFour(value));
                            updateConverted(rateInput, amountInput, feeInput, feeMode, converted, fromCurrency, toCurrency);
                            progress.status.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (progress.dialog != null && progress.dialog.isShowing()) progress.dialog.dismiss();
                                }
                            }, 600);
                        } else {
                            progress.status.setText("Unable to fetch");
                            progress.detail.setText("Please feed manually.");
                        }
                    }
                });
            }
        }).start();
    }

    private double fetchProviderRate(String fromCurrency, String toCurrency, String date) {
        if (fromCurrency.equals(toCurrency)) return 1;
        HttpURLConnection connection = null;
        try {
            String endpoint = "https://api.frankfurter.dev/v2/rate/"
                    + URLEncoder.encode(fromCurrency, "UTF-8")
                    + "/" + URLEncoder.encode(toCurrency, "UTF-8")
                    + "?date=" + URLEncoder.encode(date, "UTF-8");
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            reader.close();
            JSONObject json = new JSONObject(builder.toString());
            if (json.has("rate")) return json.getDouble("rate");
            if (json.has("rates")) {
                JSONObject rates = json.getJSONObject("rates");
                if (rates.has(toCurrency)) return rates.getDouble(toCurrency);
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return 0;
    }

    private RateFetchUi showRateFetchDialog(String title, String detail, int total, int accent) {
        LinearLayout form = form();
        addDialogHero(form, "⇄", title, detail, accent);
        RateFetchUi progress = new RateFetchUi();
        progress.status = text("Fetching... 0% complete", 22, Typeface.BOLD, accent);
        progress.detail = text("Connecting to rate provider.", 13, Typeface.NORMAL, MUTED);
        form.addView(dialogSection("Progress", progress.status, progress.detail));
        progress.dialog = showStyledDialog(form, null, "Feed manually", accent, null, null);
        return progress;
    }

    private void updateRateFetchProgress(RateFetchUi progress, int done, int total, String detail) {
        int safeTotal = Math.max(1, total);
        int percent = (int) Math.round((Math.max(0, done) * 100.0) / safeTotal);
        percent = Math.max(0, Math.min(100, percent));
        progress.status.setText("Fetching... " + percent + "% complete");
        progress.detail.setText(detail);
    }

    private void showCategoryDialog(final Category existing) {
        showCategoryDialog(existing, existing == null ? "Expense" : existing.type);
    }

    private void showCategoryDialog(final Category existing, String initialType) {
        LinearLayout form = form();
        final Spinner type = spinner(new String[]{"Expense", "Income"});
        setSpinnerToValue(type, initialType);
        final Spinner icon = spinner(categoryIconOptions());
        final EditText customIcon = input("Your icon", "", InputType.TYPE_CLASS_TEXT);
        customIcon.setVisibility(View.GONE);
        icon.setOnItemSelectedListener(customIconVisibility(icon, customIcon));
        if (existing != null) setCategoryIconSpinner(icon, customIcon, existing.icon);
        final EditText name = input("Category name", existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        addDialogHero(form, "✚", existing == null ? "Add category" : "Edit category", "Choose an icon and keep income and expense categories separate.", BLUE);
        form.addView(dialogSection("Category", dialogFieldRow(type, icon), customIcon, name));

        showStyledDialog(form, "Cancel", "Save", BLUE, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                if (name.getText().toString().trim().length() == 0) {
                    toast("Enter a category name.");
                    return false;
                }
                String iconValue = selectedCategoryIcon(icon, customIcon);
                boolean created;
                if (existing == null) {
                    created = db.addCategory(selectedCountryId, name.getText().toString().trim(),
                            iconValue, type.getSelectedItem().toString());
                } else {
                    created = db.updateCategoryForCountry(selectedCountryId, existing,
                            name.getText().toString().trim(), iconValue, type.getSelectedItem().toString());
                }
                toast(created ? "Category saved." : "Category already exists.");
                render();
                return true;
            }
        });
    }

    private void showInlineCategoryDialog(final String entryType, final Runnable afterSave) {
        LinearLayout form = form();
        final Spinner icon = spinner(categoryIconOptions());
        final EditText customIcon = input("Your icon", "", InputType.TYPE_CLASS_TEXT);
        customIcon.setVisibility(View.GONE);
        icon.setOnItemSelectedListener(customIconVisibility(icon, customIcon));
        final EditText name = input("Category name", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int accent = "Income".equals(entryType) ? GREEN : CORAL;
        addDialogHero(form, "✚", "Add " + entryType.toLowerCase(Locale.US) + " category", "Save it and continue editing this entry.", accent);
        form.addView(dialogSection("Category", icon, customIcon, name));

        showStyledDialog(form, "Cancel", "Save", accent, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                if (name.getText().toString().trim().length() == 0) {
                    toast("Enter a category name.");
                    return false;
                }
                boolean created = db.addCategory(selectedCountryId, name.getText().toString().trim(),
                        selectedCategoryIcon(icon, customIcon), entryType);
                if (!created) toast("Category already exists.");
                afterSave.run();
                return true;
            }
        });
    }

    private void showCountryDialog(final Country existing) {
        LinearLayout form = form();
        final EditText countryName = input("Name of Account", existing == null ? "" : existing.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final Spinner currency = spinner(currencyLabels());
        setSpinnerToPrefix(currency, existing == null ? "USD" : existing.currency);
        final int accent = existing == null ? BLUE : currencyColor(existing);
        addDialogHero(form, currencySymbol(existing == null ? "USD" : existing.currency),
                existing == null ? "Add account" : "Edit account",
                "Name the account and choose the currency it tracks.", accent);
        form.addView(dialogSection("Account", countryName, currency));
        final AlertDialog[] dialogHolder = new AlertDialog[1];

        dialogHolder[0] = showStyledDialog(form, "Cancel", existing == null ? "Add" : "Save", accent, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                String name = countryName.getText().toString().trim();
                if (name.length() == 0) {
                    toast("Enter an account name.");
                    return false;
                }
                if (existing == null) {
                    selectedCountryId = db.addCountry(name, selectedCurrencyCode(currency));
                    selectedAccountIds.clear();
                } else {
                    db.updateCountry(existing.id, name, selectedCurrencyCode(currency));
                }
                render();
                return true;
            }
        });
    }

    private LinearLayout dashboardFlowCard(double[] summary, Country country) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(gradient(cashFlowStart(country, summary[2]), cashFlowEnd(summary[2]), dp(22)));
        card.setElevation(dp(7));
        card.setLayoutParams(matchWrapWithMargins(0, 0, 0, dp(10)));
        View.OnClickListener openEntries = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedTab = 1;
                selectedEntryDay = today();
                selectedEntryMonth = monthToday();
                render();
            }
        };
        card.setClickable(true);
        card.setOnClickListener(openEntries);

        TextView label = text("This month · Net cash flow", 13, Typeface.BOLD, Color.argb(225, 255, 255, 255));
        label.setOnClickListener(openEntries);
        card.addView(label);
        TextView cash = text(money(summary[2], country.currency), 34, Typeface.BOLD, Color.WHITE);
        cash.setPadding(0, dp(4), 0, dp(14));
        cash.setOnClickListener(openEntries);
        card.addView(cash);

        LinearLayout row = horizontal();
        row.setOnClickListener(openEntries);
        LinearLayout income = flowPill("↑", "Income", money(summary[0], country.currency), GREEN);
        income.setOnClickListener(openEntries);
        row.addView(income);
        LinearLayout expense = flowPill("↓", "Expense", money(summary[1], country.currency), CORAL);
        expense.setOnClickListener(openEntries);
        row.addView(expense);
        card.addView(row);
        return card;
    }

    private void makeCardNavigate(View view, final int tab) {
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                selectedTab = tab;
                showEntryArchive = false;
                render();
            }
        });
    }

    private LinearLayout taxMarkedCard(Country country, String start, String end) {
        LinearLayout card = card();
        card.setBackground(round(tint(GOLD, 0.06f), dp(8), tint(GOLD, 0.20f)));
        LinearLayout row = horizontal();
        row.addView(iconBubble("▣", GOLD, tint(GOLD, 0.12f)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text("Tax marked", 13, Typeface.BOLD, GOLD));
        LinearLayout totals = horizontal();
        totals.addView(taxMarkedPill("Income", money(db.taxTotalForPeriod(selectedCountryId, "Income", start, end), country.currency), GREEN),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams expenseParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        expenseParams.setMargins(dp(8), 0, 0, 0);
        totals.addView(taxMarkedPill("Expense", money(db.taxTotalForPeriod(selectedCountryId, "Expense", start, end), country.currency), CORAL), expenseParams);
        copy.addView(totals, matchWrapWithMargins(0, dp(6), 0, 0));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        return card;
    }

    private LinearLayout taxMarkedPill(String label, String value, int color) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(dp(10), dp(8), dp(10), dp(8));
        pill.setBackground(round(tint(color, 0.08f), dp(12), tint(color, 0.22f)));
        pill.addView(text(label, 11, Typeface.BOLD, color));
        TextView amount = text(value, 14, Typeface.BOLD, color);
        amount.setSingleLine(true);
        pill.addView(amount);
        return pill;
    }

    private LinearLayout flowPill(String icon, String label, String value, int color) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(dp(11), dp(10), dp(11), dp(10));
        pill.setBackground(round(Color.argb(118, Color.red(color), Color.green(color), Color.blue(color)),
                dp(16), Color.argb(180, Color.red(color), Color.green(color), Color.blue(color))));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(0, 0, dp(8), 0);
        pill.setLayoutParams(params);
        pill.addView(text(icon + " " + label, 12, Typeface.BOLD, Color.WHITE));
        TextView valueView = text(value, 14, Typeface.BOLD, Color.WHITE);
        valueView.setSingleLine(true);
        pill.addView(valueView);
        return pill;
    }

    private LinearLayout compactMetricCard(String icon, String label, String value, int color) {
        LinearLayout card = card();
        card.setBackground(round(PAPER, dp(8), LINE));
        LinearLayout row = horizontal();
        row.addView(iconBubble(icon));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(label, 13, Typeface.BOLD, MUTED));
        copy.addView(text(value, 21, Typeface.BOLD, color));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        return card;
    }

    private LinearLayout budgetMiniCard(final BudgetProgress budget, String currency) {
        double percent = budget.limit == 0 ? 0 : (budget.spent / budget.limit) * 100.0;
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setPadding(dp(14), dp(12), dp(14), dp(12));
        makeCardNavigate(mini, 2);
        mini.setBackground(round(percent > 100 ? Color.rgb(255, 244, 243) : PAPER,
                dp(8), percent > 100 ? Color.rgb(255, 204, 200) : LINE));
        mini.setElevation(dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(10), dp(10));
        mini.setLayoutParams(params);
        LinearLayout top = horizontal();
        top.addView(text(budget.category.contains(",") ? "▦" : db.categoryIcon(selectedCountryId, budget.category), 22, Typeface.NORMAL, INK));
        TextView name = text("  " + clean(budget.name, budget.category), 15, Typeface.BOLD, INK);
        name.setSingleLine(true);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        mini.addView(top);
        mini.addView(text(money(budget.spent, currency) + " / " + money(budget.limit, currency), 12, Typeface.BOLD, percent > 100 ? CORAL : TEAL));
        mini.addView(progressBar(Math.min(100, percent), percent > 100 ? CORAL : TEAL));
        mini.addView(text(oneDecimal(percent) + "%", 11, Typeface.BOLD, MUTED));
        return mini;
    }

    private LinearLayout dailyEntryReport(final List<Entry> entries, final Country country) {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = outlineButton("‹");
        previous.setTextSize(24);
        previous.setGravity(Gravity.CENTER);
        previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shiftSelectedEntryDay(-1);
            }
        });
        Button next = outlineButton("›");
        next.setTextSize(24);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shiftSelectedEntryDay(1);
            }
        });
        TextView date = text(dayHeader(selectedEntryDay), 19, Typeface.BOLD, INK);
        date.setGravity(Gravity.CENTER);
        date.setPadding(dp(8), dp(8), dp(8), dp(8));
        date.setBackground(round(Color.rgb(238, 243, 255), dp(18), Color.rgb(214, 225, 255)));
        date.setClickable(true);
        date.setOnTouchListener(daySwipeListener());
        date.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEntryDatePicker();
            }
        });
        header.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(48)));
        header.addView(date, new LinearLayout.LayoutParams(0, dp(48), 1));
        header.addView(next, new LinearLayout.LayoutParams(dp(44), dp(48)));
        card.addView(header);

        LinearLayout tabs = horizontal();
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(round(Color.rgb(239, 243, 248), dp(18), 0));
        Button income = reportTypeButton("Income", "Income".equals(selectedEntryReportType), GREEN);
        income.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedEntryReportType = "Income";
                render();
            }
        });
        Button expense = reportTypeButton("Expense", "Expense".equals(selectedEntryReportType), CORAL);
        expense.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedEntryReportType = "Expense";
                render();
            }
        });
        tabs.addView(income, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams expenseParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        expenseParams.setMargins(dp(8), 0, 0, 0);
        tabs.addView(expense, expenseParams);
        card.addView(tabs, matchWrapWithMargins(0, dp(12), 0, dp(12)));

        List<Entry> dayEntries = entriesForDateAndType(entries, selectedEntryDay, selectedEntryReportType);
        double total = 0;
        for (Entry entry : dayEntries) total += entry.amount;
        int totalColor = "Income".equals(selectedEntryReportType) ? GREEN : CORAL;
        card.addView(entryTotalStrip(selectedEntryReportType + " total", money(total, country.currency), totalColor));

        if (dayEntries.isEmpty()) {
            card.addView(emptyText("No " + selectedEntryReportType.toLowerCase(Locale.US) + " entries for this day."));
        } else {
            final AlertDialog[] noDialog = new AlertDialog[1];
            for (Entry entry : dayEntries) card.addView(compactEntryCard(entry, country, noDialog));
        }
        return card;
    }

    private LinearLayout entryTotalStrip(String label, String value, int color) {
        LinearLayout strip = horizontal();
        strip.setPadding(dp(13), dp(12), dp(13), dp(12));
        strip.setBackground(round(tint(color, 0.10f), dp(8), tint(color, 0.22f)));
        TextView labelView = text(label, 13, Typeface.BOLD, color);
        strip.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView valueView = text(value, 22, Typeface.BOLD, color);
        valueView.setGravity(Gravity.END);
        strip.addView(valueView);
        strip.setLayoutParams(matchWrapWithMargins(0, 0, 0, dp(12)));
        return strip;
    }

    private View.OnTouchListener daySwipeListener() {
        return new View.OnTouchListener() {
            float startX;
            float startY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = event.getX();
                    startY = event.getY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - startX;
                    float dy = event.getY() - startY;
                    if (Math.abs(dx) > dp(60) && Math.abs(dx) > Math.abs(dy)) {
                        shiftSelectedEntryDay(dx < 0 ? 1 : -1);
                        return true;
                    }
                    view.performClick();
                }
                return true;
            }
        };
    }

    private void shiftSelectedEntryDay(int days) {
        selectedEntryDay = shiftDate(clean(selectedEntryDay, today()), days);
        selectedEntryMonth = safePart(selectedEntryDay, 0, 7);
        render();
    }

    private void showEntryDatePicker() {
        final EditText target = input("", clean(selectedEntryDay, today()), InputType.TYPE_CLASS_DATETIME);
        showDatePicker(target, false, new Runnable() {
            @Override
            public void run() {
                selectedEntryDay = clean(target.getText().toString(), today());
                selectedEntryMonth = safePart(selectedEntryDay, 0, 7);
                render();
            }
        });
    }

    private Button reportTypeButton(String value, boolean active, int color) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(14);
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setTextColor(active ? Color.WHITE : color);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(active ? color : Color.TRANSPARENT, dp(15), 0));
        button.setElevation(active ? dp(2) : 0);
        return button;
    }

    private LinearLayout entryMonthControls(List<Entry> entries, final Country country) {
        LinearLayout card = card();
        LinearLayout top = horizontal();
        top.addView(text("Calendar", 18, Typeface.BOLD, INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button todayButton = outlineButton("Today");
        todayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectedEntryMonth = monthToday();
                selectedEntryDay = today();
                showDayDetailsDialog(today());
            }
        });
        top.addView(todayButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        card.addView(top);

        LinearLayout pickers = horizontal();
        final Spinner month = spinner(monthOptions());
        final Spinner year = spinner(entryYearOptions(entries));
        setSpinnerToValue(month, monthNameFromMonth(selectedEntryMonth));
        setSpinnerToValue(year, safePart(selectedEntryMonth, 0, 4));
        pickers.addView(month, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        yearParams.setMargins(dp(8), 0, 0, 0);
        pickers.addView(year, yearParams);
        card.addView(pickers);
        Button view = outlineButton("View month");
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View button) {
                selectedEntryMonth = year.getSelectedItem().toString() + "-" + String.format(Locale.US, "%02d", month.getSelectedItemPosition() + 1);
                showEntryArchive = false;
                render();
            }
        });
        card.addView(view, matchWrapWithMargins(0, dp(8), 0, 0));
        card.addView(text("Tap a date for details. Swipe inside details to move day by day.", 12, Typeface.NORMAL, MUTED));
        return card;
    }

    private LinearLayout dayArchiveList(final List<Entry> entries, String month, final Country country) {
        LinearLayout card = card();
        card.addView(text(monthLabel(month), 16, Typeface.BOLD, INK));
        Set<String> renderedDays = new HashSet<String>();
        boolean added = false;
        for (Entry entry : entries) {
            if (entry.date == null || !entry.date.startsWith(month) || renderedDays.contains(entry.date)) continue;
            renderedDays.add(entry.date);
            final String date = entry.date;
            LinearLayout row = horizontal();
            row.setPadding(0, dp(8), 0, dp(8));
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showDayDetailsDialog(date);
                }
            });
            row.addView(text(date, 14, Typeface.BOLD, INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(text(daySummary(entries, date, country.currency), 12, Typeface.BOLD, MUTED));
            card.addView(row);
            added = true;
        }
        if (!added) card.addView(emptyText("No entries in this month."));
        return card;
    }

    private LinearLayout monthCalendar(final List<Entry> entries, final String month, final Country country) {
        LinearLayout calendarCard = card();
        LinearLayout header = horizontal();
        header.addView(text(monthLabel(month), 18, Typeface.BOLD, INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(text(monthSummary(entries, month, country.currency), 11, Typeface.BOLD, MUTED));
        calendarCard.addView(header);

        LinearLayout weekdays = horizontal();
        String[] labels = {"S", "M", "T", "W", "T", "F", "S"};
        for (String label : labels) {
            TextView weekday = text(label, 11, Typeface.BOLD, MUTED);
            weekday.setGravity(Gravity.CENTER);
            weekdays.addView(weekday, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        calendarCard.addView(weekdays);

        Calendar first = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(month + "-01");
            if (parsed != null) first.setTime(parsed);
        } catch (ParseException ignored) {
            calendarCard.addView(emptyText("Calendar unavailable for " + month + "."));
            return calendarCard;
        }

        int leading = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int day = 1 - leading;
        while (day <= daysInMonth) {
            LinearLayout week = horizontal();
            for (int column = 0; column < 7; column++) {
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(3), dp(4), dp(3), dp(3));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(74), 1);
                params.setMargins(dp(2), dp(2), dp(2), dp(2));
                if (day >= 1 && day <= daysInMonth) {
                    final String date = String.format(Locale.US, "%s-%02d", month, day);
                    double income = totalForDate(entries, date, "Income");
                    double expense = totalForDate(entries, date, "Expense");
                    boolean hasEntries = income > 0 || expense > 0;
                    cell.setBackground(round(selectedEntryDay.equals(date)
                            ? Color.rgb(232, 248, 245)
                            : Color.rgb(250, 251, 249), dp(8), hasEntries ? Color.rgb(190, 210, 205) : Color.rgb(232, 236, 233)));
                    TextView dayNumber = text(String.valueOf(day), 12, Typeface.BOLD, INK);
                    dayNumber.setGravity(Gravity.CENTER);
                    cell.addView(dayNumber, matchWrap());
                    TextView incomeView = text(income > 0 ? smallSignedAmount(income, true) : "", 9, Typeface.BOLD, GREEN);
                    incomeView.setGravity(Gravity.CENTER);
                    incomeView.setSingleLine(true);
                    cell.addView(incomeView, matchWrap());
                    TextView expenseView = text(expense > 0 ? smallSignedAmount(expense, false) : "", 9, Typeface.BOLD, CORAL);
                    expenseView.setGravity(Gravity.CENTER);
                    expenseView.setSingleLine(true);
                    cell.addView(expenseView, matchWrap());
                    cell.setClickable(true);
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            selectedEntryDay = date;
                            showDayDetailsDialog(date);
                        }
                    });
                } else {
                    cell.setBackground(round(Color.TRANSPARENT, dp(8), Color.TRANSPARENT));
                }
                week.addView(cell, params);
                day++;
            }
            calendarCard.addView(week);
        }
        return calendarCard;
    }

    private void showDayDetailsDialog(final String date) {
        selectedEntryDay = date;
        final Country country = db.getCountry(selectedCountryId);
        final List<Entry> entries = entriesForDate(db.entriesSince(selectedCountryId, date), date);
        final AlertDialog[] dialogRef = new AlertDialog[1];

        LinearLayout form = form();
        form.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = outlineButton("‹");
        previous.setTextSize(24);
        previous.setGravity(Gravity.CENTER);
        previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                showDayDetailsDialog(shiftDate(date, -1));
            }
        });
        Button next = outlineButton("›");
        next.setTextSize(24);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                showDayDetailsDialog(shiftDate(date, 1));
            }
        });
        TextView title = text(date, 17, Typeface.BOLD, INK);
        title.setGravity(Gravity.CENTER);
        header.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(48)));
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        header.addView(next, new LinearLayout.LayoutParams(dp(44), dp(48)));
        form.addView(header);

        if (entries.isEmpty()) {
            form.addView(emptyText("No entries for this date."));
        } else {
            for (Entry entry : entries) form.addView(compactEntryCard(entry, country, dialogRef));
        }

        View.OnTouchListener swipeListener = new View.OnTouchListener() {
            float startX;
            float startY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = event.getX();
                    startY = event.getY();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float deltaX = event.getX() - startX;
                    float deltaY = event.getY() - startY;
                    if (Math.abs(deltaX) > dp(60) && Math.abs(deltaX) > Math.abs(deltaY)) {
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        showDayDetailsDialog(shiftDate(date, deltaX < 0 ? 1 : -1));
                        return true;
                    }
                }
                return false;
            }
        };
        form.setOnTouchListener(swipeListener);
        dialogRef[0] = showStyledDialog(form, null, "Done", currencyColor(country), null, null, swipeListener);
    }

    private LinearLayout compactEntryCard(final Entry entry, final Country country, final AlertDialog[] parentDialog) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(11), dp(12), dp(11));
        boolean income = "Income".equals(entry.type);
        int amountColor = income ? GREEN : CORAL;
        card.setBackground(round(PAPER, dp(8), income ? tint(GREEN, 0.28f) : tint(CORAL, 0.28f)));
        LinearLayout top = horizontal();
        top.addView(iconBubble(db.categoryIcon(selectedCountryId, entry.category), amountColor, tint(amountColor, 0.12f)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(entry.title, 16, Typeface.BOLD, INK);
        copy.addView(title);
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView amount = text((income ? "+" : "-") + money(entry.amount, country.currency), 14, Typeface.BOLD, amountColor);
        amount.setGravity(Gravity.END);
        top.addView(amount);
        card.addView(top);

        LinearLayout icons = horizontal();
        icons.setPadding(dp(52), 0, 0, 0);
        if (entry.repeatInterval > 0) icons.addView(text("🔁 " + repeatCompactLabel(entry), 12, Typeface.BOLD, MUTED));
        if (entry.taxFlag) icons.addView(text((entry.repeatInterval > 0 ? "  " : "") + "🧾 " + money(entry.taxAmount, country.currency), 12, Typeface.BOLD, GOLD));
        icons.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (entry.billUri.length() > 0) {
            Button bill = outlineButton("🧾");
            bill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openBill(entry.billUri);
                }
            });
            icons.addView(bill, smallActionParams());
        }
        if (entry.id > 0 && sourceEntryEditable(entry)) {
            Button edit = tinyIconButton("✎", BLUE);
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (parentDialog != null && parentDialog[0] != null) parentDialog[0].dismiss();
                    showEntryDialog(entry.type, entry);
                }
            });
            icons.addView(edit, tinyActionParams());

            Button delete = tinyIconButton("🗑", CORAL);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete("Delete this entry?", new Runnable() {
                        @Override
                        public void run() {
                            db.deleteEntry(entry.id);
                            if (parentDialog != null && parentDialog[0] != null) parentDialog[0].dismiss();
                            render();
                            showDayDetailsDialog(selectedEntryDay);
                        }
                    });
                }
            });
            icons.addView(delete, tinyActionParams());
        }
        card.addView(icons);
        return card;
    }

    private LinearLayout budgetCard(final BudgetProgress budget, String currency, boolean withDelete) {
        double percent = budget.limit == 0 ? 0 : (budget.spent / budget.limit) * 100.0;
        LinearLayout card = card();
        LinearLayout row = horizontal();
        row.addView(iconBubble(budget.category.contains(",") ? "▦" : db.categoryIcon(selectedCountryId, budget.category)));
        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        title.addView(text(clean(budget.name, budget.category), 17, Typeface.BOLD, INK));
        title.addView(text(budget.category, 12, Typeface.NORMAL, MUTED));
        title.addView(text((budget.recurring ? "Repeats monthly from " : "Month: ") + budget.month, 12, Typeface.NORMAL, MUTED));
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row);
        card.addView(metricRow("Spent", money(budget.spent, currency), percent > 100 ? CORAL : TEAL));
        card.addView(metricRow("Limit", money(budget.limit, currency), INK));
        card.addView(progressBar(Math.min(100, percent), percent > 100 ? CORAL : TEAL));
        card.addView(text(oneDecimal(percent) + "% used", 12, Typeface.NORMAL, MUTED));
        if (budget.notes.length() > 0) card.addView(text(budget.notes, 13, Typeface.NORMAL, INK));
        if (withDelete) {
            LinearLayout actions = horizontal();
            Button edit = outlineButton("Edit");
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showBudgetDialog(budget);
                }
            });
            actions.addView(edit, smallActionParams());
            Button delete = outlineButton("Delete");
            delete.setTextColor(CORAL);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete("Delete this budget?", new Runnable() {
                        @Override
                        public void run() {
                            db.deleteById("budgets", budget.id);
                            render();
                        }
                    });
                }
            });
            actions.addView(delete, smallActionParams());
            card.addView(actions);
        }
        return card;
    }

    private LinearLayout goalCard(final Goal goal, String currency, boolean withActions) {
        double percent = goal.targetAmount == 0 ? 0 : (goal.currentAmount / goal.targetAmount) * 100.0;
        LinearLayout card = card();
        if (!withActions) makeCardNavigate(card, 5);
        LinearLayout title = horizontal();
        title.addView(iconBubble("🎯"));
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(text(goal.title, 17, Typeface.BOLD, INK));
        details.addView(text("Target date: " + blankAsDash(goal.targetDate), 12, Typeface.NORMAL, MUTED));
        details.addView(text(goal.accountId == 0 ? "Not linked to an account" : "Set aside in " + goal.accountName, 12, Typeface.BOLD, BLUE));
        title.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(title);
        card.addView(metricRow("Set aside", money(goal.currentAmount, currency), TEAL));
        card.addView(metricRow("Target", money(goal.targetAmount, currency), INK));
        card.addView(progressBar(Math.min(100, percent), percent >= 100 ? GREEN : TEAL));
        card.addView(text(oneDecimal(percent) + "% complete", 12, Typeface.NORMAL, MUTED));
        if (goal.notes.length() > 0) card.addView(text(goal.notes, 13, Typeface.NORMAL, INK));
        if (withActions) {
            LinearLayout actions = horizontal();
            Button edit = outlineButton("Edit");
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showGoalDialog(goal);
                }
            });
            actions.addView(edit, smallActionParams());
            if (goal.accountId == 0) {
                Button link = outlineButton("Set account");
                link.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showGoalAccountDialog(goal);
                    }
                });
                actions.addView(link, smallActionParams());
            }
            Button add = outlineButton("＋ Progress");
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showGoalContributionDialog(goal);
                }
            });
            actions.addView(add, smallActionParams());
            Button delete = outlineButton("Delete");
            delete.setTextColor(CORAL);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmDelete("Delete this goal?", new Runnable() {
                        @Override
                        public void run() {
                            db.deleteById("goals", goal.id);
                            render();
                        }
                    });
                }
            });
            actions.addView(delete, smallActionParams());
            card.addView(actions);
        }
        return card;
    }

    private void applyDailyRate(List<Country> countries, Spinner fromCountry, Spinner toCountry, EditText date,
                                EditText rate, EditText amount, EditText fee, RadioGroup feeMode, TextView converted) {
        Country from = countries.get(fromCountry.getSelectedItemPosition());
        Country to = countries.get(toCountry.getSelectedItemPosition());
        String rateDate = clean(date.getText().toString(), today());
        double dailyRate = db.dailyRate(from.currency, to.currency, rateDate);
        rate.setText(dailyRate > 0 ? oneFour(dailyRate) : "");
        updateConverted(rate, amount, fee, feeMode, converted, from.currency, to.currency);
    }

    private void updateConverted(EditText rate, EditText amount, EditText fee, RadioGroup feeMode, TextView converted,
                                 String fromCurrency, String toCurrency) {
        double rateValue = parseDouble(rate.getText().toString());
        double amountValue = parseDouble(amount.getText().toString());
        double feeValue = Math.max(0, parseDouble(fee.getText().toString()));
        if (rateValue > 0 && amountValue > 0) {
            boolean inclusiveFee = feeMode != null && isFeeInclusive(feeMode);
            double convertedAmount = inclusiveFee ? Math.max(0, amountValue - feeValue) : amountValue;
            double deductedAmount = inclusiveFee ? amountValue : amountValue + feeValue;
            converted.setText("≈ " + money(convertedAmount * rateValue, toCurrency)
                    + " received · " + money(deductedAmount, fromCurrency) + " deducted"
                    + (inclusiveFee ? " · fee included" : " · fee extra"));
        } else {
            converted.setText("Set the daily rate to calculate the received amount.");
        }
    }

    private void openBill(String uriValue) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(uriValue));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            toast("No app can open this bill.");
        }
    }

    private void addActionHeader(String title, String actionTitle, View.OnClickListener listener) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, 22, Typeface.BOLD, INK);
        row.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button button = primaryButton(actionTitle);
        button.setOnClickListener(listener);
        row.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        content.addView(row, matchWrap());
        content.addView(spacer(10));
    }

    private void addSectionTitle(String title) {
        TextView view = text(title, 16, Typeface.BOLD, INK);
        view.setPadding(dp(2), dp(12), 0, dp(6));
        content.addView(view);
    }

    private LinearLayout metricRow(String label, String value, int color) {
        LinearLayout row = horizontal();
        row.setPadding(0, dp(5), 0, dp(5));
        row.addView(text(label, 14, Typeface.NORMAL, MUTED), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(text(value, 15, Typeface.BOLD, color));
        return row;
    }

    private LinearLayout actionPanel(String title, String action, int color) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(12), dp(12), dp(12), dp(12));
        panel.setBackground(round(Color.WHITE, dp(14), Color.rgb(225, 230, 227)));
        panel.setElevation(dp(2));
        panel.setClickable(true);
        panel.addView(text(action, 26, Typeface.BOLD, color));
        TextView label = text(title, 15, Typeface.BOLD, INK);
        label.setGravity(Gravity.CENTER);
        panel.addView(label);
        return panel;
    }

    private LinearLayout entryActionPanel(String title, String action, int color) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(12), dp(16), dp(12), dp(16));
        panel.setBackground(round(tint(color, 0.10f), dp(8), tint(color, 0.22f)));
        panel.setElevation(dp(4));
        panel.setClickable(true);
        TextView symbol = text(action, 24, Typeface.BOLD, Color.WHITE);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(round(color, dp(19), 0));
        panel.addView(symbol, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView label = text(title, 17, Typeface.BOLD, INK);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);
        panel.addView(label, matchWrap());
        return panel;
    }

    private LinearLayout expandRow(boolean expanded, String title, String summary, View.OnClickListener listener) {
        LinearLayout row = horizontal();
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(Color.WHITE, dp(12), Color.rgb(225, 230, 227)));
        row.setClickable(true);
        row.setOnClickListener(listener);
        row.addView(text(expanded ? "−" : "+", 22, Typeface.BOLD, TEAL));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 16, Typeface.BOLD, INK);
        copy.addView(titleView);
        copy.addView(text(summary, 12, Typeface.NORMAL, MUTED));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(10), 0, 0, 0);
        row.addView(copy, params);
        row.setLayoutParams(matchWrapWithMargins(0, dp(6), 0, dp(4)));
        return row;
    }

    private AutoCompleteTextView titleInput(String entryType) {
        AutoCompleteTextView edit = new AutoCompleteTextView(this);
        edit.setHint("Title");
        edit.setSingleLine(true);
        edit.setTextColor(INK);
        edit.setHintTextColor(Color.rgb(126, 136, 145));
        edit.setTextSize(15);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setTypeface(friendlyTypeface(Typeface.NORMAL));
        edit.setMinHeight(dp(56));
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(round(Color.rgb(248, 250, 253), dp(16), Color.rgb(214, 225, 240)));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line,
                db.previousTitles(selectedCountryId, entryType));
        edit.setAdapter(adapter);
        edit.setThreshold(1);
        return edit;
    }

    private LinearLayout miniStat(String label, String value, String icon) {
        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.VERTICAL);
        stat.setGravity(Gravity.CENTER);
        stat.setPadding(dp(8), dp(8), dp(8), dp(8));
        stat.setBackground(round(Color.argb(42, 255, 255, 255), dp(14), Color.argb(70, 255, 255, 255)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        stat.setLayoutParams(params);
        TextView iconView = text(icon, 16, Typeface.BOLD, Color.WHITE);
        iconView.setGravity(Gravity.CENTER);
        stat.addView(iconView);
        TextView labelView = text(label, 11, Typeface.BOLD, Color.rgb(222, 250, 244));
        labelView.setGravity(Gravity.CENTER);
        stat.addView(labelView);
        TextView valueView = text(value, 12, Typeface.BOLD, Color.WHITE);
        valueView.setGravity(Gravity.CENTER);
        valueView.setSingleLine(true);
        stat.addView(valueView);
        return stat;
    }

    private TextView iconBubble(String icon) {
        int accent = activeAccent();
        return iconBubble(icon, accent, tint(accent, 0.10f));
    }

    private TextView iconBubble(String icon, int iconColor, int fillColor) {
        TextView view = text(icon, 22, Typeface.NORMAL, iconColor);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(fillColor, dp(18), 0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(42), dp(42));
        params.setMargins(0, 0, dp(10), 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView profileAvatar(Country country, boolean large) {
        String label = avatarLabel(country);
        TextView avatar = text(label, large ? 15 : 13, Typeface.BOLD, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setSingleLine(false);
        int accent = currencyColor(country);
        avatar.setBackground(gradient(accent, blend(accent, Color.rgb(255, 255, 255)), large ? dp(31) : dp(24)));
        avatar.setElevation(large ? dp(5) : dp(2));
        int size = large ? dp(62) : dp(48);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(dp(10), 0, 0, 0);
        avatar.setLayoutParams(params);
        return avatar;
    }

    private int activeAccent() {
        if (db != null && selectedCountryId != 0) return currencyColor(db.getCountry(selectedCountryId));
        return BLUE;
    }

    private String avatarLabel(Country country) {
        String cleaned = country.name == null ? "" : country.name.trim();
        String initials = "";
        if (cleaned.length() > 0) {
            String[] words = cleaned.split("\\s+");
            for (int i = 0; i < words.length && initials.length() < 2; i++) {
                if (words[i].length() > 0) initials += words[i].substring(0, 1).toUpperCase(Locale.US);
            }
        }
        if (initials.length() == 0 && country.currency != null && country.currency.length() >= 2) {
            initials = country.currency.substring(0, 2);
        }
        if (initials.length() == 0) initials = "FT";
        return initials + "\n" + currencySymbol(country.currency);
    }

    private int currencyColor(Country country) {
        int[] palette = {
                Color.rgb(15, 118, 110),
                Color.rgb(41, 98, 180),
                Color.rgb(176, 76, 62),
                Color.rgb(128, 84, 177),
                Color.rgb(184, 117, 22),
                Color.rgb(24, 121, 143),
                Color.rgb(190, 73, 128),
                Color.rgb(52, 111, 63),
                Color.rgb(92, 92, 198),
                Color.rgb(180, 95, 54),
                Color.rgb(0, 125, 115),
                Color.rgb(116, 82, 55)
        };
        if (db != null && country != null && country.id != 0) {
            List<Country> countries = db.getCountries();
            for (int i = 0; i < countries.size(); i++) {
                if (countries.get(i).id == country.id) return palette[i % palette.length];
            }
        }
        String seed = (country == null ? "" : country.currency) + ":" + (country == null ? 0 : country.id);
        int hash = Math.abs(seed.hashCode());
        return palette[hash % palette.length];
    }

    private int blend(int left, int right) {
        return Color.rgb((Color.red(left) + Color.red(right)) / 2,
                (Color.green(left) + Color.green(right)) / 2,
                (Color.blue(left) + Color.blue(right)) / 2);
    }

    private int tint(int color, float strength) {
        float clamped = Math.max(0f, Math.min(1f, strength));
        int red = (int) (Color.red(color) * clamped + 255 * (1f - clamped));
        int green = (int) (Color.green(color) * clamped + 255 * (1f - clamped));
        int blue = (int) (Color.blue(color) * clamped + 255 * (1f - clamped));
        return Color.rgb(red, green, blue);
    }

    private View progressBar(double percent, int color) {
        LinearLayout frame = new LinearLayout(this);
        frame.setBackground(round(Color.rgb(232, 237, 245), dp(8), 0));
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        frameParams.setMargins(0, dp(8), 0, dp(6));
        frame.setLayoutParams(frameParams);

        View fill = new View(this);
        fill.setBackground(round(color, dp(8), 0));
        LinearLayout.LayoutParams fillParams = new LinearLayout.LayoutParams(0, dp(10));
        fillParams.weight = (float) Math.max(0, Math.min(100, percent));
        frame.addView(fill, fillParams);
        View rest = new View(this);
        LinearLayout.LayoutParams restParams = new LinearLayout.LayoutParams(0, dp(10));
        restParams.weight = (float) Math.max(0, 100 - Math.min(100, percent));
        frame.addView(rest, restParams);
        return frame;
    }

    private LinearLayout cardWith(View view) {
        LinearLayout card = card();
        card.addView(view);
        return card;
    }

    private AlertDialog showStyledDialog(LinearLayout form, String cancelText, String primaryText, int accent,
                                         final DialogCancel onCancel, final DialogSubmit onSubmit) {
        return showStyledDialog(form, cancelText, primaryText, accent, onCancel, onSubmit, null);
    }

    private AlertDialog showStyledDialog(LinearLayout form, String cancelText, String primaryText, int accent,
                                         final DialogCancel onCancel, final DialogSubmit onSubmit,
                                         View.OnTouchListener scrollTouchListener) {
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        currentDialogAccent = accent;
        int dialogTint = tint(accent, 0.18f);
        GradientDrawable dialogBackground = gradient(dialogTint, tint(blend(accent, BLUE), 0.20f), dp(24));
        form.setBackground(dialogBackground);
        harmonizeDialogView(form, accent);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(gradient(dialogTint, tint(blend(accent, BLUE), 0.20f), dp(24)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackground(gradient(dialogTint, tint(blend(accent, BLUE), 0.20f), dp(24)));
        if (scrollTouchListener != null) scroll.setOnTouchListener(scrollTouchListener);
        scroll.addView(form);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(14), dp(12), dp(14), dp(14));
        actions.setBackground(round(tint(accent, 0.16f), dp(20), tint(accent, 0.34f)));
        if (cancelText != null) {
            Button cancel = modalSecondaryButton(cancelText);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (onCancel != null) onCancel.cancel();
                    dialog.dismiss();
                }
            });
            actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        if (primaryText != null) {
            Button primary = modalPrimaryButton(primaryText, accent);
            primary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (onSubmit == null || onSubmit.submit()) dialog.dismiss();
                }
            });
            LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (cancelText != null) primaryParams.setMargins(dp(10), 0, 0, 0);
            actions.addView(primary, primaryParams);
        }
        panel.addView(actions, matchWrap());

        dialog.setView(panel);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setDimAmount(0.62f);
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
            }
        });
        dialog.show();
        return dialog;
    }

    private void addDialogHero(LinearLayout form, String icon, String title, String subtitle, int color) {
        currentDialogAccent = color;
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(14), dp(14), dp(14), dp(14));
        hero.setBackground(gradient(color, blend(color, BLUE), dp(18)));
        hero.setElevation(dp(4));

        TextView iconView = text(icon, 24, Typeface.BOLD, Color.WHITE);
        iconView.setGravity(Gravity.CENTER);
        iconView.setBackground(round(Color.argb(42, 255, 255, 255), dp(22), Color.argb(80, 255, 255, 255)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconParams.setMargins(0, 0, dp(12), 0);
        hero.addView(iconView, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 20, Typeface.BOLD, Color.WHITE));
        copy.addView(text(subtitle, 12, Typeface.NORMAL, Color.argb(230, 255, 255, 255)));
        hero.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        form.addView(hero, matchWrapWithMargins(0, 0, 0, dp(12)));
    }

    private LinearLayout dialogSection(String title, View... children) {
        int accent = currentDialogAccent;
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(12), dp(12), dp(12));
        section.setBackground(round(tint(accent, 0.07f), dp(18), tint(accent, 0.28f)));
        section.setElevation(dp(3));
        if (title != null && title.length() > 0) {
            TextView titleView = text(title, 12, Typeface.BOLD, accent);
            titleView.setPadding(0, 0, 0, dp(6));
            section.addView(titleView, matchWrap());
        }
        for (int i = 0; i < children.length; i++) {
            section.addView(children[i], matchWrapWithMargins(0, i == 0 ? 0 : dp(8), 0, 0));
        }
        section.setLayoutParams(matchWrapWithMargins(0, 0, 0, dp(10)));
        return section;
    }

    private LinearLayout dialogFieldRow(View left, View right) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rightParams.setMargins(dp(8), 0, 0, 0);
        row.addView(right, rightParams);
        return row;
    }

    private void harmonizeDialogView(View view, int accent) {
        if (view instanceof EditText) {
            restyleDialogField(view, accent);
            return;
        }
        if (view instanceof Spinner) {
            restyleDialogField(view, accent);
            return;
        }
        if (view instanceof CheckBox) {
            CheckBox checkBox = (CheckBox) view;
            checkBox.setTextColor(INK);
            checkBox.setTypeface(friendlyTypeface(Typeface.BOLD));
            checkBox.setBackground(round(tint(accent, 0.07f), dp(16), tint(accent, 0.24f)));
            return;
        }
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setTextColor(accent);
            button.setTypeface(friendlyTypeface(Typeface.BOLD));
            button.setBackground(round(tint(accent, 0.06f), dp(16), tint(accent, 0.26f)));
            return;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            int color = textView.getCurrentTextColor();
            if (color == MUTED || color == BLUE) textView.setTextColor(accent);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                harmonizeDialogView(group.getChildAt(i), accent);
            }
        }
    }

    private void restyleDialogField(View view, int accent) {
        view.setBackground(round(tint(accent, 0.04f), dp(16), tint(accent, 0.24f)));
        view.setPadding(dp(14), 0, dp(14), 0);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(PAPER, dp(8), LINE));
        card.setElevation(dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private TextView text(String value, int sp, int style, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(friendlyTypeface(style));
        view.setIncludeFontPadding(true);
        return view;
    }

    private void loadFonts() {
        try {
            interTypeface = getResources().getFont(R.font.inter);
        } catch (Exception ignored) {
            interTypeface = null;
        }
    }

    private Typeface friendlyTypeface(int style) {
        if (interTypeface != null) return Typeface.create(interTypeface, style);
        return Typeface.create(style == Typeface.BOLD ? "sans-serif-medium" : "sans-serif", style);
    }

    private TextView label(String value) {
        TextView view = text(value, 12, Typeface.BOLD, currentDialogAccent);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private TextView emptyText(String value) {
        TextView view = text(value, 14, Typeface.NORMAL, MUTED);
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private void styleDialogCheckBox(CheckBox checkBox, String value) {
        checkBox.setText(value);
        checkBox.setTextColor(INK);
        checkBox.setTextSize(14);
        checkBox.setTypeface(friendlyTypeface(Typeface.BOLD));
        checkBox.setMinHeight(dp(48));
        checkBox.setPadding(dp(8), 0, dp(8), 0);
        checkBox.setBackground(round(tint(currentDialogAccent, 0.07f), dp(16), tint(currentDialogAccent, 0.24f)));
    }

    private EditText input(String hint, String value, int inputType) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextColor(INK);
        edit.setHintTextColor(Color.rgb(126, 136, 145));
        edit.setTextSize(15);
        edit.setInputType(inputType);
        edit.setTypeface(friendlyTypeface(Typeface.NORMAL));
        edit.setMinHeight(dp(56));
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(round(Color.rgb(248, 250, 253), dp(16), Color.rgb(214, 225, 240)));
        return edit;
    }

    private EditText dateInput(String hint, String value, final boolean allowEmpty) {
        final EditText edit = input(hint + " (tap to pick)", value, InputType.TYPE_CLASS_DATETIME);
        edit.setFocusable(false);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker(edit, allowEmpty);
            }
        });
        return edit;
    }

    private void showDatePicker(final EditText target, boolean allowEmpty) {
        showDatePicker(target, allowEmpty, null);
    }

    private void showDatePicker(final EditText target, boolean allowEmpty, final Runnable afterSet) {
        Calendar calendar = Calendar.getInstance();
        String value = target.getText().toString().trim();
        if (value.length() > 0) {
            try {
                Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
                if (parsed != null) calendar.setTime(parsed);
            } catch (ParseException ignored) {
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker datePicker, int year, int month, int day) {
                target.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day));
                if (afterSet != null) afterSet.run();
            }
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        if (allowEmpty) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Clear", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    target.setText("");
                }
            });
        }
        dialog.show();
    }

    private EditText multiInput(String hint) {
        EditText edit = input(hint, "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setMinLines(2);
        edit.setSingleLine(false);
        edit.setGravity(Gravity.TOP | Gravity.START);
        return edit;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(56));
        spinner.setPadding(dp(12), 0, dp(12), 0);
        spinner.setBackground(round(Color.rgb(248, 250, 253), dp(16), Color.rgb(214, 225, 240)));
        return spinner;
    }

    private RadioGroup feeModeRadioGroup() {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        group.setPadding(dp(8), dp(8), dp(8), dp(8));
        group.setBackground(round(Color.rgb(248, 250, 253), dp(16), Color.rgb(214, 225, 240)));
        RadioButton exclusive = feeModeRadio("Fee exclusive", false);
        RadioButton inclusive = feeModeRadio("Fee inclusive", true);
        group.addView(exclusive, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        group.addView(inclusive, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        group.check(exclusive.getId());
        return group;
    }

    private RadioButton feeModeRadio(String label, boolean inclusive) {
        RadioButton radio = new RadioButton(this);
        radio.setId(View.generateViewId());
        radio.setTag(inclusive ? "inclusive" : "exclusive");
        radio.setText(label);
        radio.setTextSize(13);
        radio.setTextColor(INK);
        radio.setTypeface(friendlyTypeface(Typeface.BOLD));
        radio.setButtonTintList(android.content.res.ColorStateList.valueOf(SKY));
        return radio;
    }

    private boolean isFeeInclusive(RadioGroup group) {
        View checked = group.findViewById(group.getCheckedRadioButtonId());
        return checked != null && "inclusive".equals(checked.getTag());
    }

    private void resetSpinner(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setMinimumHeight(dp(56));
        spinner.setPadding(dp(12), 0, dp(12), 0);
        spinner.setBackground(round(Color.rgb(248, 250, 253), dp(16), Color.rgb(214, 225, 240)));
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setBackground(round(BLUE, dp(18), 0));
        button.setElevation(dp(4));
        return button;
    }

    private Button modalPrimaryButton(String value, int accent) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setBackground(round(accent, dp(20), 0));
        button.setElevation(dp(3));
        return button;
    }

    private Button modalSecondaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setTextSize(15);
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setBackground(round(tint(currentDialogAccent, 0.06f), dp(20), tint(currentDialogAccent, 0.26f)));
        return button;
    }

    private Button menuButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(INK);
        button.setTextSize(24);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, dp(2));
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setBackground(round(PAPER, dp(23), LINE));
        button.setElevation(dp(4));
        return button;
    }

    private Button ghostButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setBackground(round(Color.argb(45, 255, 255, 255), dp(12), Color.argb(90, 255, 255, 255)));
        return button;
    }

    private Button roundIconButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(26);
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(Color.argb(48, 255, 255, 255), dp(22), Color.argb(90, 255, 255, 255)));
        return button;
    }

    private Button outlineButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(BLUE);
        button.setTextSize(13);
        button.setTypeface(friendlyTypeface(Typeface.BOLD));
        button.setBackground(round(PAPER, dp(16), LINE));
        return button;
    }

    private Button tinyIconButton(String value, int color) {
        Button button = outlineButton(value);
        button.setTextColor(color);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, dp(1));
        button.setBackground(round(tint(color, 0.06f), dp(14), tint(color, 0.20f)));
        return button;
    }

    private Button bottomTabButton(String value, boolean active, int accent) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setTypeface(friendlyTypeface(active ? Typeface.BOLD : Typeface.NORMAL));
        button.setTextColor(active ? Color.WHITE : MUTED);
        button.setBackground(active ? gradient(accent, blend(accent, CASH_BLUE), dp(16))
                : round(Color.argb(132, 255, 255, 255), dp(16), 0));
        return button;
    }

    private Button chip(String value, boolean active) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(active ? Color.WHITE : INK);
        button.setTypeface(friendlyTypeface(active ? Typeface.BOLD : Typeface.NORMAL));
        button.setBackground(round(active ? BLUE : PAPER, dp(18), active ? 0 : LINE));
        return button;
    }

    private Button tabButton(String value, boolean active) {
        Button button = chip(value, active);
        button.setMinHeight(dp(40));
        return button;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private int cashFlowStart(Country country, double netCashFlow) {
        int accent = currencyColor(country);
        int mood = netCashFlow < 0 ? blend(CORAL, CASH_BLUE) : CASH_MINT;
        return blend(mood, accent);
    }

    private int cashFlowEnd(double netCashFlow) {
        return netCashFlow < 0 ? blend(CORAL, APP_ICE) : CASH_BLUE;
    }

    private GradientDrawable revolutBackground() {
        return gradient(APP_MINT, APP_ICE, 0);
    }

    private LinearLayout form() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(14);
        layout.setPadding(pad, pad, pad, pad);
        layout.setBackgroundColor(SURFACE);
        return layout;
    }

    private ScrollView scrollWrap(View view) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(SURFACE);
        scroll.addView(view);
        return scroll;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(Color.rgb(232, 235, 233));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.setMargins(0, dp(10), 0, dp(10));
        view.setLayoutParams(params);
        return view;
    }

    private View spacer(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private GradientDrawable round(int fill, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(0, 0, dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams bottomTabParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(104), dp(54));
        params.setMargins(0, 0, dp(6), 0);
        return params;
    }

    private LinearLayout.LayoutParams smallActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(0, dp(8), dp(8), 0);
        return params;
    }

    private LinearLayout.LayoutParams tinyActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.setMargins(dp(6), 0, 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String monthToday() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private String[] monthOptions() {
        return new String[]{"January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};
    }

    private String monthNameFromMonth(String month) {
        int value = 1;
        try {
            value = Integer.parseInt(safePart(month, 5, 7));
        } catch (Exception ignored) {
        }
        value = Math.max(1, Math.min(12, value));
        return monthOptions()[value - 1];
    }

    private String[] entryYearOptions(List<Entry> entries) {
        Set<String> years = new HashSet<String>();
        Calendar calendar = Calendar.getInstance();
        int thisYear = calendar.get(Calendar.YEAR);
        for (int i = 0; i < 5; i++) years.add(String.valueOf(thisYear - i));
        for (Entry entry : entries) {
            String year = safePart(entry.date, 0, 4);
            if (year.length() == 4) years.add(year);
        }
        List<String> result = new ArrayList<String>(years);
        Collections.sort(result, Collections.reverseOrder());
        return result.toArray(new String[result.size()]);
    }

    private static String money(double amount, String currency) {
        String sign = amount < 0 ? "-" : "";
        return sign + currencySymbol(currency) + String.format(Locale.US, "%,.2f", Math.abs(amount));
    }

    private static String currencySymbol(String currencyCode) {
        try {
            Currency currency = Currency.getInstance(currencyCode);
            String symbol = currency.getSymbol(Locale.getDefault());
            return symbol == null || symbol.trim().length() == 0 ? currencyCode : symbol;
        } catch (Exception ignored) {
            return currencyCode == null ? "" : currencyCode.trim();
        }
    }

    private String moneyPlain(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private String signedMoney(double amount, String currency) {
        return (amount >= 0 ? "+" : "") + money(amount, currency);
    }

    private String oneDecimal(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private String oneFour(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    private String subcategoryLabel(String subcategory) {
        return subcategory == null || subcategory.trim().length() == 0 ? "" : " / " + subcategory;
    }

    private String blankAsDash(String value) {
        return value == null || value.trim().length() == 0 ? "-" : value;
    }

    private String fiveYearsAgo() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -5);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private String safePart(String value, int start, int end) {
        if (value == null || value.length() < end) return value == null ? "" : value;
        return value.substring(start, end);
    }

    private String monthLabel(String month) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM", Locale.US);
        SimpleDateFormat output = new SimpleDateFormat("MMMM yyyy", Locale.US);
        try {
            Date date = input.parse(month);
            return date == null ? month : output.format(date);
        } catch (ParseException ignored) {
            return month;
        }
    }

    private String dayHeader(String date) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat output = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.US);
        try {
            Date parsed = input.parse(date);
            return parsed == null ? date : output.format(parsed);
        } catch (ParseException ignored) {
            return date;
        }
    }

    private void toggleSet(Set<String> set, String key) {
        if (set.contains(key)) set.remove(key);
        else set.add(key);
    }

    private String repeatLabel(Entry entry) {
        return entry.repeatInterval > 0 ? " · repeats every " + entry.repeatInterval + " " + entry.repeatUnit.toLowerCase(Locale.US) : "";
    }

    private String repeatCompactLabel(Entry entry) {
        String unit = entry.repeatUnit == null ? "" : entry.repeatUnit.toLowerCase(Locale.US);
        String label = "every " + entry.repeatInterval + " " + unit;
        if (entry.repeatCount > 0) return label + " · till " + dayHeader(recurringOccurrenceDate(entry, entry.repeatCount));
        return label;
    }

    private int remainingRepeatCount(Entry entry) {
        if (entry.repeatCount <= 0 || entry.repeatInterval <= 0) return 0;
        int elapsed = elapsedRepeatIntervals(entry);
        return Math.max(0, entry.repeatCount - elapsed);
    }

    private int elapsedRepeatIntervals(Entry entry) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(entry.date);
            if (parsed != null) start.setTime(parsed);
        } catch (ParseException ignored) {
            return 0;
        }
        long diffDays = Math.max(0, (end.getTimeInMillis() - start.getTimeInMillis()) / 86400000L);
        if ("Days".equals(entry.repeatUnit)) return (int) (diffDays / Math.max(1, entry.repeatInterval));
        if ("Weeks".equals(entry.repeatUnit)) return (int) (diffDays / (7L * Math.max(1, entry.repeatInterval)));
        if ("Months".equals(entry.repeatUnit)) {
            int months = (end.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 + end.get(Calendar.MONTH) - start.get(Calendar.MONTH);
            return Math.max(0, months / Math.max(1, entry.repeatInterval));
        }
        if ("Years".equals(entry.repeatUnit)) {
            int years = end.get(Calendar.YEAR) - start.get(Calendar.YEAR);
            return Math.max(0, years / Math.max(1, entry.repeatInterval));
        }
        return 0;
    }

    private List<Entry> entriesForDate(List<Entry> entries, String date) {
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (date.equals(entry.date)) result.add(entry);
        }
        return result;
    }

    private List<Entry> entriesForDateAndType(List<Entry> entries, String date, String type) {
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (date.equals(entry.date) && type.equals(entry.type)) result.add(entry);
        }
        return result;
    }

    private List<Entry> entriesForMonth(List<Entry> entries, String month) {
        List<Entry> result = new ArrayList<Entry>();
        for (Entry entry : entries) {
            if (entry.date != null && entry.date.startsWith(month)) result.add(entry);
        }
        return result;
    }

    private double totalForDate(List<Entry> entries, String date, String type) {
        double total = 0;
        for (Entry entry : entries) {
            if (date.equals(entry.date) && type.equals(entry.type)) total += entry.amount;
        }
        return total;
    }

    private String smallSignedAmount(double amount, boolean income) {
        double value = Math.abs(amount);
        String suffix = "";
        if (value >= 1000000) {
            value = value / 1000000.0;
            suffix = "m";
        } else if (value >= 1000) {
            value = value / 1000.0;
            suffix = "k";
        }
        String format = suffix.length() == 0 ? "%,.0f" : "%.1f";
        return (income ? "+" : "-") + String.format(Locale.US, format, value) + suffix;
    }

    private String shiftDate(String date, int days) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            if (parsed != null) calendar.setTime(parsed);
        } catch (ParseException ignored) {
        }
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private String recurringDueCutoffDate() {
        return today();
    }

    private String recurringOccurrenceDate(Entry entry, int occurrence) {
        Calendar calendar = Calendar.getInstance();
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(entry.date);
            if (parsed == null) return "";
            calendar.setTime(parsed);
        } catch (ParseException ignored) {
            return "";
        }
        int distance = Math.max(1, entry.repeatInterval) * Math.max(1, occurrence);
        if ("Days".equals(entry.repeatUnit)) calendar.add(Calendar.DAY_OF_MONTH, distance);
        else if ("Weeks".equals(entry.repeatUnit)) calendar.add(Calendar.DAY_OF_MONTH, distance * 7);
        else if ("Months".equals(entry.repeatUnit)) calendar.add(Calendar.MONTH, distance);
        else if ("Years".equals(entry.repeatUnit)) calendar.add(Calendar.YEAR, distance);
        else return "";
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private String yearSummary(List<Entry> entries, String year, String currency) {
        double income = 0;
        double expense = 0;
        for (Entry entry : entries) {
            if (entry.date != null && entry.date.startsWith(year)) {
                if ("Income".equals(entry.type)) income += entry.amount;
                else expense += entry.amount;
            }
        }
        return "Income " + money(income, currency) + " · Expense " + money(expense, currency);
    }

    private String monthSummary(List<Entry> entries, String month, String currency) {
        double income = 0;
        double expense = 0;
        for (Entry entry : entries) {
            if (entry.date != null && entry.date.startsWith(month)) {
                if ("Income".equals(entry.type)) income += entry.amount;
                else expense += entry.amount;
            }
        }
        return "Income " + money(income, currency) + " · Expense " + money(expense, currency);
    }

    private String daySummary(List<Entry> entries, String date, String currency) {
        double income = 0;
        double expense = 0;
        for (Entry entry : entries) {
            if (date.equals(entry.date)) {
                if ("Income".equals(entry.type)) income += entry.amount;
                else expense += entry.amount;
            }
        }
        return "+" + money(income, currency) + " / -" + money(expense, currency);
    }

    private String checkedCategoryNames(List<Category> categories, List<CheckBox> checks) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < checks.size() && i < categories.size(); i++) {
            if (checks.get(i).isChecked()) {
                if (builder.length() > 0) builder.append(",");
                builder.append(categories.get(i).name);
            }
        }
        return builder.toString();
    }

    private int checkedCategoryCount(List<CheckBox> checks) {
        int count = 0;
        for (CheckBox check : checks) if (check.isChecked()) count++;
        return count;
    }

    private void updateBudgetNameVisibility(EditText name, List<Category> categories, List<CheckBox> checks) {
        int count = checkedCategoryCount(checks);
        name.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
        if (count == 1) name.setText(checkedCategoryNames(categories, checks));
    }

    private boolean categoryListContains(String categoryList, String category) {
        if (categoryList == null || category == null) return false;
        String[] parts = categoryList.split(",");
        for (String part : parts) {
            if (part.trim().equalsIgnoreCase(category.trim())) return true;
        }
        return false;
    }

    private String clean(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.length() == 0 ? fallback : cleaned;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private double parseDouble(String value) {
        if (value == null) return 0;
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int decimalInput() {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    }

    private int signedDecimalInput() {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
    }

    private String[] accountNames(List<Account> accounts) {
        if (accounts.isEmpty()) return new String[]{"No accounts"};
        String[] names = new String[accounts.size()];
        for (int i = 0; i < accounts.size(); i++) names[i] = accountIcon(accounts.get(i).type) + " " + accounts.get(i).name;
        return names;
    }

    private String[] entryAccountNames(List<Account> accounts) {
        String[] names = new String[accounts.size() + 1];
        for (int i = 0; i < accounts.size(); i++) names[i] = accountIcon(accounts.get(i).type) + " " + accounts.get(i).name;
        names[accounts.size()] = "＋ Add account";
        return names;
    }

    private String[] countryNames(List<Country> countries) {
        String[] names = new String[countries.size()];
        for (int i = 0; i < countries.size(); i++) names[i] = countries.get(i).name + " · " + countries.get(i).currency;
        return names;
    }

    private int countryIndex(List<Country> countries, long countryId) {
        for (int i = 0; i < countries.size(); i++) {
            if (countries.get(i).id == countryId) return i;
        }
        return 0;
    }

    private void setAccountSelection(Spinner spinner, List<Account> accounts, long accountId) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id == accountId) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String[] categoryLabels(List<Category> categories) {
        String[] labels = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) labels[i] = categories.get(i).icon + " " + categories.get(i).name;
        return labels;
    }

    private String[] categoryRemoveLabels(List<Category> categories) {
        String[] labels = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            labels[i] = category.icon + " " + category.name + " · " + category.type;
        }
        return labels;
    }

    private String[] subcategoriesFor(List<Category> categories, int position) {
        if (categories.isEmpty()) return new String[]{"None"};
        String raw = categories.get(Math.max(0, Math.min(position, categories.size() - 1))).subcategories;
        if (raw == null || raw.trim().length() == 0) return new String[]{"None"};
        String[] parts = raw.split(",");
        List<String> values = new ArrayList<String>();
        values.add("None");
        for (String part : parts) {
            String cleaned = part.trim();
            if (cleaned.length() > 0) values.add(cleaned);
        }
        return values.toArray(new String[values.size()]);
    }

    private String[] withNone(String[] values) {
        String[] result = new String[values.length + 1];
        result[0] = "None";
        for (int i = 0; i < values.length; i++) result[i + 1] = values[i];
        return result;
    }

    private void ensureAccountSelection(List<Account> accounts) {
        Set<Long> valid = new HashSet<Long>();
        for (Account account : accounts) valid.add(account.id);
        selectedAccountIds.retainAll(valid);
        if (selectedAccountIds.isEmpty()) {
            for (Account account : accounts) selectedAccountIds.add(account.id);
        }
    }

    private String accountIcon(String type) {
        if ("Cash".equals(type)) return "💵";
        if ("Credit Card".equals(type)) return "💳";
        if ("Loan".equals(type)) return "🏦";
        if ("Wallet".equals(type)) return "👛";
        if ("Brokerage".equals(type)) return "📈";
        if ("Bank".equals(type)) return "🏛";
        return "▣";
    }

    private String investmentIcon(String type) {
        if ("Fixed Deposit".equals(type)) return "🏦";
        if ("Recurring Deposit".equals(type)) return "🔁";
        return "📈";
    }

    private String[] categoryIconOptions() {
        return new String[]{
                "🛒 Grocery", "🛍 Shopping", "🍼 Baby", "🚕 Transport", "🍽 Dining",
                "🏠 Housing", "💡 Utilities", "🩺 Health", "📚 Education", "✈ Travel",
                "🎬 Entertainment", "🧾 Tax", "💼 Work", "📈 Investment", "🎁 Gifts",
                "💸 Fees", "🏋 Fitness", "🔧 Repairs", "🛡 Insurance", "✎ Custom"
        };
    }

    private String selectedIcon(Spinner spinner) {
        String value = spinner.getSelectedItem().toString();
        int space = value.indexOf(" ");
        return space > 0 ? value.substring(0, space) : value;
    }

    private AdapterView.OnItemSelectedListener customIconVisibility(final Spinner spinner, final EditText customIcon) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
                customIcon.setVisibility(isCustomIconSelection(spinner) ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        };
    }

    private boolean isCustomIconSelection(Spinner spinner) {
        return spinner.getSelectedItem() != null && spinner.getSelectedItem().toString().contains("Custom");
    }

    private String selectedCategoryIcon(Spinner spinner, EditText customIcon) {
        if (isCustomIconSelection(spinner)) return clean(customIcon.getText().toString(), "✦");
        return selectedIcon(spinner);
    }

    private void setCategoryIconSpinner(Spinner spinner, EditText customIcon, String icon) {
        if (icon == null || icon.length() == 0) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            String value = spinner.getItemAtPosition(i).toString();
            if (value.startsWith(icon + " ")) {
                spinner.setSelection(i);
                return;
            }
        }
        spinner.setSelection(spinner.getCount() - 1);
        customIcon.setText(icon);
        customIcon.setVisibility(View.VISIBLE);
    }

    private String[] currencyLabels() {
        List<Currency> currencies = new ArrayList<Currency>(Currency.getAvailableCurrencies());
        Collections.sort(currencies, new Comparator<Currency>() {
            @Override
            public int compare(Currency left, Currency right) {
                return left.getCurrencyCode().compareTo(right.getCurrencyCode());
            }
        });
        List<String> labels = new ArrayList<String>();
        for (Currency currency : currencies) {
            labels.add(currency.getCurrencyCode() + " · " + currency.getSymbol(Locale.getDefault()) + " · " + currency.getDisplayName(Locale.US));
        }
        return labels.toArray(new String[labels.size()]);
    }

    private String selectedCurrencyCode(Spinner spinner) {
        String value = spinner.getSelectedItem().toString();
        int index = value.indexOf(" ");
        return index > 0 ? value.substring(0, index) : value;
    }

    private AutoCompleteTextView currencySearchInput(String currencyCode) {
        AutoCompleteTextView edit = new AutoCompleteTextView(this);
        edit.setHint("Search currency, e.g. INR");
        edit.setSingleLine(true);
        edit.setTextColor(INK);
        edit.setHintTextColor(Color.rgb(126, 136, 145));
        edit.setTextSize(15);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        edit.setTypeface(friendlyTypeface(Typeface.NORMAL));
        edit.setMinHeight(dp(56));
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(round(Color.rgb(248, 250, 253), dp(16), Color.rgb(214, 225, 240)));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, currencyLabels());
        edit.setAdapter(adapter);
        edit.setThreshold(1);
        setCurrencySearchText(edit, currencyCode);
        return edit;
    }

    private void setCurrencySearchText(AutoCompleteTextView edit, String currencyCode) {
        String[] labels = currencyLabels();
        for (String label : labels) {
            if (label.startsWith(currencyCode + " ")) {
                edit.setText(label, false);
                return;
            }
        }
        edit.setText(currencyCode, false);
    }

    private String selectedCurrencyCode(AutoCompleteTextView edit) {
        String value = edit.getText().toString().trim().toUpperCase(Locale.US);
        if (value.length() >= 3) {
            String code = value.substring(0, 3);
            try {
                Currency.getInstance(code);
                return code;
            } catch (Exception ignored) {
            }
        }
        String compact = value.replace(" ", "");
        for (String label : currencyLabels()) {
            String upper = label.toUpperCase(Locale.US);
            if (upper.startsWith(value) || upper.contains(value) || upper.replace(" ", "").contains(compact)) {
                return label.substring(0, 3);
            }
        }
        return "USD";
    }

    private void setSpinnerToPrefix(Spinner spinner, String prefix) {
        for (int i = 0; i < spinner.getCount(); i++) {
            String item = spinner.getItemAtPosition(i).toString();
            if (item.startsWith(prefix + " ")) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void setSpinnerToValue(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void setCategorySpinner(Spinner spinner, List<Category> categories, String name) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).name.equals(name)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String currencyForCountry(String countryCode) {
        try {
            Locale locale = new Locale("", countryCode);
            return Currency.getInstance(locale).getCurrencyCode();
        } catch (Exception ignored) {
            return "USD";
        }
    }

    private void confirmDelete(String message, final Runnable deleteAction) {
        LinearLayout form = form();
        addDialogHero(form, "🗑", "Confirm delete", message, CORAL);
        showStyledDialog(form, "Cancel", "Delete", CORAL, null, new DialogSubmit() {
            @Override
            public boolean submit() {
                deleteAction.run();
                return true;
            }
        });
    }

    private double estimateInvestmentValue(Investment investment) {
        double days = daysBetween(investment.startDate, today());
        double years = Math.max(0, days / 365.0);
        if ("Recurring Deposit".equals(investment.type)) {
            return investment.principal * (1.0 + (investment.annualRate / 100.0) * Math.max(0.1, years / 2.0));
        }
        return investment.principal * (1.0 + (investment.annualRate / 100.0) * years);
    }

    private double daysBetween(String start, String end) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        try {
            Date startDate = formatter.parse(start);
            Date endDate = formatter.parse(end);
            if (startDate == null || endDate == null) return 0;
            long diff = endDate.getTime() - startDate.getTime();
            return diff / 86400000.0;
        } catch (ParseException ignored) {
            return 0;
        }
    }

    private String displayNameForUri(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "";
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private static class Country {
        long id;
        String name;
        String currency;
    }

    private static class Account {
        long id;
        String name;
        String type;
        double balance;
        String currency;
    }

    private static class Category {
        long id;
        long countryId;
        String name;
        String icon;
        String type;
        String subcategories;
    }

    private static class Entry {
        long id;
        String type;
        String date;
        String title;
        double amount;
        String category;
        String subcategory;
        String accountName;
        String notes;
        String billUri;
        boolean taxFlag;
        double taxAmount;
        String taxTitle;
        long accountId;
        int repeatInterval;
        String repeatUnit;
        int repeatCount;
        String sourceType;
    }

    private static class PendingRecurringEntry {
        Entry template;
        String date;
        int occurrence;
        boolean approvable;
    }

    private static class Investment {
        long id;
        String type;
        String title;
        long accountId;
        String accountName;
        double principal;
        double currentValue;
        double annualRate;
        String startDate;
        String maturityDate;
        double maturityValue;
        String maturityAction;
        String status;
        String notes;
    }

    private static class InvestmentDraft {
        String type = "Fixed Deposit";
        String title = "";
        long accountId;
        double principal;
        String startDate = "";
        String maturityDate = "";
        double maturityValue;
        String maturityAction = "";
        String notes = "";
        boolean reinvested;
    }

    private static class EntryTemplate {
        String category = "";
        double amount;
        String notes = "";
    }

    private static class Goal {
        long id;
        long accountId;
        String accountName;
        String title;
        double targetAmount;
        double currentAmount;
        String targetDate;
        String notes;
    }

    private static class BudgetProgress {
        long id;
        String name;
        String category;
        String subcategory;
        String month;
        double limit;
        double spent;
        String notes;
        boolean recurring;
    }

    private static class RateView {
        String fromCurrency;
        String toCurrency;
        double rate;
        String source;
        String date;
    }

    private static class Transfer {
        long id;
        long fromCountryId;
        long toCountryId;
        long fromAccountId;
        long toAccountId;
        String date;
        String fromCountry;
        String toCountry;
        String fromAccount;
        String toAccount;
        String fromCurrency;
        String toCurrency;
        double fromAmount;
        double toAmount;
        double rate;
        double feeAmount;
        String rateSource;
        String notes;
    }

    private static class FinanceDb extends SQLiteOpenHelper {
        private static final String DB_NAME = "finance_tracker.db";
        private static final int DB_VERSION = 8;

        FinanceDb(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createTables(db);
            seedDefaultCategories(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                createV2Tables(db);
                seedDefaultCategories(db);
            }
            if (oldVersion < 3) {
                migrateV3(db);
            }
            if (oldVersion < 4) {
                migrateV4(db);
            }
            if (oldVersion < 5) {
                migrateV5(db);
            }
            if (oldVersion < 6) {
                migrateV6(db);
            }
            if (oldVersion < 7) {
                migrateV7(db);
            }
            if (oldVersion < 8) {
                migrateV8(db);
            }
        }

        private void createTables(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE app_meta (key TEXT PRIMARY KEY, value TEXT)");
            db.execSQL("CREATE TABLE countries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL," +
                    "currency TEXT NOT NULL)");
            db.execSQL("CREATE TABLE accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "country_id INTEGER NOT NULL," +
                    "name TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "balance REAL NOT NULL DEFAULT 0," +
                    "currency TEXT NOT NULL," +
                    "created_at TEXT NOT NULL)");
            db.execSQL("CREATE TABLE entries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "country_id INTEGER NOT NULL," +
                    "type TEXT NOT NULL," +
                    "date TEXT NOT NULL," +
                    "title TEXT NOT NULL," +
                    "amount REAL NOT NULL," +
                    "category TEXT NOT NULL," +
                    "subcategory TEXT," +
                    "account_id INTEGER NOT NULL," +
                    "notes TEXT," +
                    "bill_uri TEXT," +
                    "tax_flag INTEGER NOT NULL DEFAULT 0," +
                    "tax_amount REAL NOT NULL DEFAULT 0," +
                    "tax_title TEXT," +
                    "repeat_interval INTEGER NOT NULL DEFAULT 0," +
                    "repeat_unit TEXT," +
                    "repeat_count INTEGER NOT NULL DEFAULT 0," +
                    "source_type TEXT," +
                    "source_id INTEGER NOT NULL DEFAULT 0," +
                    "affects_account INTEGER NOT NULL DEFAULT 1," +
                    "created_at TEXT NOT NULL)");
            db.execSQL("CREATE TABLE investments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "country_id INTEGER NOT NULL," +
                    "type TEXT NOT NULL," +
                    "title TEXT NOT NULL," +
                    "account_id INTEGER NOT NULL DEFAULT 0," +
                    "principal REAL NOT NULL," +
                    "current_value REAL NOT NULL DEFAULT 0," +
                    "annual_rate REAL NOT NULL DEFAULT 0," +
                    "start_date TEXT NOT NULL," +
                    "maturity_date TEXT," +
                    "maturity_value REAL NOT NULL DEFAULT 0," +
                    "maturity_action TEXT," +
                    "status TEXT NOT NULL DEFAULT 'Active'," +
                    "notes TEXT," +
                    "created_at TEXT NOT NULL)");
            db.execSQL("CREATE TABLE goals (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "country_id INTEGER NOT NULL," +
                    "account_id INTEGER NOT NULL DEFAULT 0," +
                    "title TEXT NOT NULL," +
                    "target_amount REAL NOT NULL," +
                    "current_amount REAL NOT NULL DEFAULT 0," +
                    "target_date TEXT," +
                    "notes TEXT," +
                    "created_at TEXT NOT NULL)");
            db.execSQL("CREATE TABLE budgets (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "country_id INTEGER NOT NULL," +
                    "name TEXT," +
                    "category TEXT NOT NULL," +
                    "subcategory TEXT," +
                    "month TEXT NOT NULL," +
                    "limit_amount REAL NOT NULL," +
                    "notes TEXT," +
                    "is_recurring INTEGER NOT NULL DEFAULT 1," +
                    "created_at TEXT NOT NULL)");
            createV2Tables(db);
        }

        private void createV2Tables(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS app_meta (key TEXT PRIMARY KEY, value TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS categories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "country_id INTEGER NOT NULL DEFAULT 0," +
                    "name TEXT NOT NULL," +
                    "icon TEXT NOT NULL," +
                    "type TEXT NOT NULL DEFAULT 'Expense'," +
                    "subcategories TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS exchange_rates (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "month TEXT NOT NULL," +
                    "from_currency TEXT NOT NULL," +
                    "to_currency TEXT NOT NULL," +
                    "rate REAL NOT NULL," +
                    "source TEXT NOT NULL DEFAULT 'Manual'," +
                    "updated_at TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS transfers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "date TEXT NOT NULL," +
                    "from_country_id INTEGER NOT NULL," +
                    "to_country_id INTEGER NOT NULL," +
                    "from_account_id INTEGER NOT NULL," +
                    "to_account_id INTEGER NOT NULL," +
                    "from_amount REAL NOT NULL," +
                    "to_amount REAL NOT NULL," +
                    "rate REAL NOT NULL," +
                    "fee_amount REAL NOT NULL DEFAULT 0," +
                    "rate_source TEXT," +
                    "notes TEXT," +
                    "created_at TEXT NOT NULL)");
            createCategoryRemovalTable(db);
        }

        private void migrateV3(SQLiteDatabase db) {
            addColumnIfMissing(db, "categories", "type TEXT NOT NULL DEFAULT 'Expense'");
            addColumnIfMissing(db, "entries", "repeat_interval INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "entries", "repeat_unit TEXT");
            addColumnIfMissing(db, "investments", "maturity_value REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "investments", "maturity_action TEXT");
            addColumnIfMissing(db, "investments", "status TEXT NOT NULL DEFAULT 'Active'");
            db.execSQL("UPDATE categories SET type = 'Income' WHERE name IN ('Salary','Investments')");
            db.execSQL("DELETE FROM categories WHERE country_id = 0 AND name = 'Other'");
            seedMissingDefaultCategories(db);
            db.execSQL("UPDATE investments SET maturity_value = current_value WHERE maturity_value = 0 AND current_value > 0");
            db.execSQL("UPDATE investments SET maturity_value = principal WHERE maturity_value = 0");
            db.execSQL("UPDATE investments SET maturity_action = 'Return full maturity amount to linked account' WHERE maturity_action IS NULL OR maturity_action = ''");
        }

        private void migrateV4(SQLiteDatabase db) {
            addColumnIfMissing(db, "budgets", "name TEXT");
            addColumnIfMissing(db, "budgets", "is_recurring INTEGER NOT NULL DEFAULT 1");
            db.execSQL("UPDATE budgets SET name = category WHERE name IS NULL OR name = ''");
        }

        private void migrateV5(SQLiteDatabase db) {
            addColumnIfMissing(db, "entries", "repeat_count INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "exchange_rates", "source TEXT NOT NULL DEFAULT 'Manual'");
            addColumnIfMissing(db, "transfers", "fee_amount REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "transfers", "rate_source TEXT");
            addColumnIfMissing(db, "goals", "account_id INTEGER NOT NULL DEFAULT 0");
            seedMissingDefaultCategories(db);
        }

        private void migrateV6(SQLiteDatabase db) {
            addColumnIfMissing(db, "entries", "source_type TEXT");
            addColumnIfMissing(db, "entries", "source_id INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "entries", "affects_account INTEGER NOT NULL DEFAULT 1");
            db.execSQL("UPDATE entries SET affects_account = 1 WHERE affects_account IS NULL");
            db.execSQL("UPDATE entries SET affects_account = 0 WHERE notes IN ('Auto-added from currency transfer','Auto-added from investment maturity')");
            seedMissingDefaultCategories(db);
        }

        private void migrateV7(SQLiteDatabase db) {
            seedMissingDefaultCategories(db);
            backfillTransferEntries(db);
        }

        private void migrateV8(SQLiteDatabase db) {
            createCategoryRemovalTable(db);
            cleanupDuplicateCategories(db);
        }

        private void createCategoryRemovalTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS category_removals (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "country_id INTEGER NOT NULL," +
                    "name TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "created_at TEXT NOT NULL)");
        }

        private void cleanupDuplicateCategories(SQLiteDatabase db) {
            db.execSQL("DELETE FROM categories WHERE id NOT IN (" +
                    "SELECT MIN(id) FROM categories GROUP BY country_id, LOWER(TRIM(name)), type)");
            db.execSQL("DELETE FROM categories WHERE country_id != 0 AND EXISTS (" +
                    "SELECT 1 FROM categories d " +
                    "WHERE d.country_id = 0 " +
                    "AND LOWER(TRIM(d.name)) = LOWER(TRIM(categories.name)) " +
                    "AND d.type = categories.type)");
            db.execSQL("DELETE FROM category_removals WHERE id NOT IN (" +
                    "SELECT MIN(id) FROM category_removals GROUP BY country_id, LOWER(TRIM(name)), type)");
        }

        private void backfillTransferEntries(SQLiteDatabase db) {
            Cursor cursor = db.rawQuery(
                    "SELECT id, date, from_country_id, to_country_id, from_account_id, to_account_id, from_amount, to_amount, fee_amount " +
                            "FROM transfers",
                    null);
            try {
                while (cursor.moveToNext()) {
                    long transferId = cursor.getLong(0);
                    String date = cursor.getString(1);
                    long fromCountryId = cursor.getLong(2);
                    long toCountryId = cursor.getLong(3);
                    long fromAccountId = cursor.getLong(4);
                    long toAccountId = cursor.getLong(5);
                    double fromAmount = cursor.getDouble(6);
                    double toAmount = cursor.getDouble(7);
                    double feeAmount = cursor.getDouble(8);
                    if (countInDb(db, "SELECT COUNT(*) FROM entries WHERE source_type = ? AND source_id = ?",
                            SOURCE_TRANSFER_OUT, String.valueOf(transferId)) == 0) {
                        insertEntry(db, fromCountryId, "Expense", date, "Transfer to receiving account", fromAmount,
                                "Transfers", "", fromAccountId, "Auto-added from currency transfer", "",
                                false, 0, "", 0, "", 0, SOURCE_TRANSFER_OUT, transferId, false);
                    }
                    if (countInDb(db, "SELECT COUNT(*) FROM entries WHERE source_type = ? AND source_id = ?",
                            SOURCE_TRANSFER_IN, String.valueOf(transferId)) == 0) {
                        insertEntry(db, toCountryId, "Income", date, "Transfer from source account", toAmount,
                                "Transfers", "", toAccountId, "Auto-added from currency transfer", "",
                                false, 0, "", 0, "", 0, SOURCE_TRANSFER_IN, transferId, false);
                    }
                    if (feeAmount > 0 && countInDb(db, "SELECT COUNT(*) FROM entries WHERE source_type = ? AND source_id = ?",
                            SOURCE_TRANSFER_FEE, String.valueOf(transferId)) == 0) {
                        ContentValues source = new ContentValues();
                        source.put("source_type", SOURCE_TRANSFER_FEE);
                        source.put("source_id", transferId);
                        source.put("affects_account", 0);
                        int updated = db.update("entries", source,
                                "type = 'Expense' AND title = 'Transfer fee' AND date = ? AND account_id = ? AND amount = ? " +
                                        "AND notes = 'Auto-added from currency transfer' AND (source_type IS NULL OR source_type = '')",
                                new String[]{date, String.valueOf(fromAccountId), String.valueOf(feeAmount)});
                        if (updated == 0) {
                            insertEntry(db, fromCountryId, "Expense", date, "Transfer fee", feeAmount,
                                    "Fees", "", fromAccountId, "Auto-added from currency transfer", "",
                                    false, 0, "", 0, "", 0, SOURCE_TRANSFER_FEE, transferId, false);
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }

        void ensureRuntimeSchema() {
            SQLiteDatabase db = getWritableDatabase();
            createV2Tables(db);
            migrateV3(db);
            migrateV4(db);
            migrateV5(db);
            migrateV6(db);
            migrateV7(db);
            migrateV8(db);
        }

        private void addColumnIfMissing(SQLiteDatabase db, String table, String definition) {
            String trimmed = definition == null ? "" : definition.trim();
            if (trimmed.length() == 0) return;
            String column = trimmed.split("\\s+")[0];
            if (columnExists(db, table, column)) return;
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + trimmed);
            } catch (Exception ignored) {
            }
        }

        private boolean columnExists(SQLiteDatabase db, String table, String column) {
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if (column.equalsIgnoreCase(cursor.getString(nameIndex))) return true;
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
            return false;
        }

        private void seedDefaultCategories(SQLiteDatabase db) {
            Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM categories WHERE country_id = 0", null);
            try {
                if (cursor.moveToFirst() && cursor.getLong(0) > 0) return;
            } finally {
                cursor.close();
            }
            seedMissingDefaultCategories(db);
        }

        private void seedMissingDefaultCategories(SQLiteDatabase db) {
            addDefaultCategory(db, "Salary", "💼", "Income");
            addDefaultCategory(db, "Side Income", "🧩", "Income");
            addDefaultCategory(db, "Cashback", "↩", "Income");
            addDefaultCategory(db, "Dividends", "📈", "Income");
            addDefaultCategory(db, "Interest", "🏦", "Income");
            addDefaultCategory(db, "Investments", "📈", "Income");
            addDefaultCategory(db, "Grocery", "🛒", "Expense");
            addDefaultCategory(db, "Shopping", "🛍", "Expense");
            addDefaultCategory(db, "Baby Care", "🍼", "Expense");
            addDefaultCategory(db, "Transportation", "🚕", "Expense");
            addDefaultCategory(db, "Food & Dining", "🍽", "Expense");
            addDefaultCategory(db, "Housing", "🏠", "Expense");
            addDefaultCategory(db, "Utilities", "💡", "Expense");
            addDefaultCategory(db, "Health", "🩺", "Expense");
            addDefaultCategory(db, "Education", "📚", "Expense");
            addDefaultCategory(db, "Travel", "✈", "Expense");
            addDefaultCategory(db, "Entertainment", "🎬", "Expense");
            addDefaultCategory(db, "Taxes", "🧾", "Expense");
            addDefaultCategory(db, "Insurance", "🛡", "Expense");
            addDefaultCategory(db, "Fees", "💸", "Expense");
            addDefaultCategory(db, "Investments", "📈", "Expense");
            addDefaultCategory(db, "Transfers", "⇄", "Income");
            addDefaultCategory(db, "Transfers", "⇄", "Expense");
            addDefaultCategory(db, "Balance Adjustment", "≋", "Income");
            addDefaultCategory(db, "Balance Adjustment", "≋", "Expense");
        }

        private void addDefaultCategory(SQLiteDatabase db, String name, String icon, String type) {
            if (countInDb(db, "SELECT COUNT(*) FROM categories WHERE country_id = 0 AND name = ? AND type = ?", name, type) > 0) return;
            ContentValues values = new ContentValues();
            values.put("country_id", 0);
            values.put("name", name);
            values.put("icon", icon);
            values.put("type", type);
            values.put("subcategories", "");
            db.insert("categories", null, values);
        }

        boolean isOnboarded() {
            return getMeta("first_name").length() > 0 && count("SELECT COUNT(*) FROM countries") > 0;
        }

        String getMeta(String key) {
            Cursor cursor = getReadableDatabase().rawQuery("SELECT value FROM app_meta WHERE key = ?", new String[]{key});
            try {
                if (cursor.moveToFirst()) return safe(cursor.getString(0));
            } finally {
                cursor.close();
            }
            return "";
        }

        void setMeta(String key, String value) {
            ContentValues values = new ContentValues();
            values.put("key", key);
            values.put("value", value);
            getWritableDatabase().insertWithOnConflict("app_meta", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        void clearStarterCountriesIfEmpty() {
            if (count("SELECT COUNT(*) FROM entries") > 0 || count("SELECT COUNT(*) FROM investments") > 0
                    || count("SELECT COUNT(*) FROM goals") > 0 || count("SELECT COUNT(*) FROM budgets") > 0
                    || count("SELECT COUNT(*) FROM transfers") > 0) {
                return;
            }
            Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM countries WHERE name NOT IN ('Country 1', 'Country 2')", null);
            try {
                if (cursor.moveToFirst() && cursor.getLong(0) > 0) return;
            } finally {
                cursor.close();
            }
            SQLiteDatabase db = getWritableDatabase();
            db.delete("accounts", null, null);
            db.delete("countries", null, null);
        }

        void resetAllData() {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete("entries", null, null);
                db.delete("investments", null, null);
                db.delete("goals", null, null);
                db.delete("budgets", null, null);
                db.delete("transfers", null, null);
                db.delete("exchange_rates", null, null);
                db.delete("accounts", null, null);
                db.delete("countries", null, null);
                db.delete("categories", null, null);
                db.delete("category_removals", null, null);
                db.delete("app_meta", null, null);
                db.delete("sqlite_sequence", "name IN (?,?,?,?,?,?,?,?,?)", new String[]{
                        "countries", "accounts", "entries", "investments", "goals",
                        "budgets", "categories", "exchange_rates", "transfers"
                });
                seedMissingDefaultCategories(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        long firstCountryId() {
            Cursor cursor = getReadableDatabase().rawQuery("SELECT id FROM countries ORDER BY id LIMIT 1", null);
            try {
                if (cursor.moveToFirst()) return cursor.getLong(0);
            } finally {
                cursor.close();
            }
            return 0;
        }

        long addCountry(String name, String currency) {
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("currency", currency);
            long id = getWritableDatabase().insert("countries", null, values);
            addAccount(id, "Cash Wallet", "Cash", 0, currency);
            return id;
        }

        List<Country> getCountries() {
            List<Country> result = new ArrayList<Country>();
            Cursor cursor = getReadableDatabase().rawQuery("SELECT id, name, currency FROM countries ORDER BY name", null);
            try {
                while (cursor.moveToNext()) {
                    Country country = new Country();
                    country.id = cursor.getLong(0);
                    country.name = cursor.getString(1);
                    country.currency = cursor.getString(2);
                    result.add(country);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        Country getCountry(long id) {
            Cursor cursor = getReadableDatabase().rawQuery("SELECT id, name, currency FROM countries WHERE id = ?", new String[]{String.valueOf(id)});
            try {
                if (cursor.moveToFirst()) {
                    Country country = new Country();
                    country.id = cursor.getLong(0);
                    country.name = cursor.getString(1);
                    country.currency = cursor.getString(2);
                    return country;
                }
            } finally {
                cursor.close();
            }
            Country fallback = new Country();
            fallback.id = 0;
            fallback.name = "";
            fallback.currency = "USD";
            return fallback;
        }

        void updateCountry(long id, String name, String currency) {
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("currency", currency);
            SQLiteDatabase db = getWritableDatabase();
            db.update("countries", values, "id = ?", new String[]{String.valueOf(id)});
            ContentValues accountValues = new ContentValues();
            accountValues.put("currency", currency);
            db.update("accounts", accountValues, "country_id = ?", new String[]{String.valueOf(id)});
        }

        boolean canDeleteCountry(long countryId) {
            return count("SELECT COUNT(*) FROM entries WHERE country_id = ?", String.valueOf(countryId)) == 0
                    && count("SELECT COUNT(*) FROM investments WHERE country_id = ?", String.valueOf(countryId)) == 0
                    && count("SELECT COUNT(*) FROM goals WHERE country_id = ?", String.valueOf(countryId)) == 0
                    && count("SELECT COUNT(*) FROM budgets WHERE country_id = ?", String.valueOf(countryId)) == 0
                    && count("SELECT COUNT(*) FROM transfers WHERE from_country_id = ? OR to_country_id = ?", String.valueOf(countryId), String.valueOf(countryId)) == 0;
        }

        void deleteCountry(long countryId) {
            SQLiteDatabase db = getWritableDatabase();
            db.delete("accounts", "country_id = ?", new String[]{String.valueOf(countryId)});
            db.delete("categories", "country_id = ?", new String[]{String.valueOf(countryId)});
            db.delete("countries", "id = ?", new String[]{String.valueOf(countryId)});
        }

        void addAccount(long countryId, String name, String type, double balance, String currency) {
            ContentValues values = new ContentValues();
            values.put("country_id", countryId);
            values.put("name", name);
            values.put("type", type);
            values.put("balance", balance);
            values.put("currency", currency);
            values.put("created_at", now());
            getWritableDatabase().insert("accounts", null, values);
        }

        void updateAccount(long accountId, String name, String type, double balance, String currency) {
            SQLiteDatabase db = getWritableDatabase();
            long countryId = 0;
            double oldBalance = 0;
            Cursor cursor = db.rawQuery("SELECT country_id, balance FROM accounts WHERE id = ?", new String[]{String.valueOf(accountId)});
            try {
                if (cursor.moveToFirst()) {
                    countryId = cursor.getLong(0);
                    oldBalance = cursor.getDouble(1);
                }
            } finally {
                cursor.close();
            }
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("type", type);
            values.put("balance", balance);
            values.put("currency", currency);
            db.beginTransaction();
            try {
                db.update("accounts", values, "id = ?", new String[]{String.valueOf(accountId)});
                double difference = balance - oldBalance;
                if (countryId > 0 && Math.abs(difference) > 0.0001) {
                    insertEntry(db, countryId, difference >= 0 ? "Income" : "Expense",
                            new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()),
                            "Balance adjustment", Math.abs(difference), "Balance Adjustment", "",
                            accountId, "Auto-added from account balance edit", "",
                            false, 0, "", 0, "", 0, SOURCE_BALANCE_ADJUSTMENT, accountId, false);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        List<Account> accounts(long countryId) {
            List<Account> result = new ArrayList<Account>();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT id, name, type, balance, currency FROM accounts WHERE country_id = ? ORDER BY name",
                    new String[]{String.valueOf(countryId)});
            try {
                while (cursor.moveToNext()) {
                    Account account = new Account();
                    account.id = cursor.getLong(0);
                    account.name = cursor.getString(1);
                    account.type = cursor.getString(2);
                    account.balance = cursor.getDouble(3);
                    account.currency = cursor.getString(4);
                    result.add(account);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        Account account(long accountId) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT id, name, type, balance, currency FROM accounts WHERE id = ?",
                    new String[]{String.valueOf(accountId)});
            try {
                if (cursor.moveToFirst()) {
                    Account account = new Account();
                    account.id = cursor.getLong(0);
                    account.name = cursor.getString(1);
                    account.type = cursor.getString(2);
                    account.balance = cursor.getDouble(3);
                    account.currency = cursor.getString(4);
                    return account;
                }
            } finally {
                cursor.close();
            }
            Account empty = new Account();
            empty.name = "Account";
            empty.currency = "USD";
            return empty;
        }

        List<Entry> accountEntries(long countryId, long accountId) {
            List<Entry> result = new ArrayList<Entry>();
            String sql = "SELECT e.id, e.type, e.date, e.title, e.amount, e.category, e.subcategory, " +
                    "COALESCE(a.name, 'Unknown account'), e.notes, e.bill_uri, e.tax_flag, e.tax_amount, e.tax_title, e.account_id, e.repeat_interval, e.repeat_unit, e.repeat_count, e.source_type " +
                    "FROM entries e LEFT JOIN accounts a ON a.id = e.account_id " +
                    "WHERE e.country_id = ? AND e.account_id = ? ORDER BY e.date DESC, e.id DESC";
            Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(countryId), String.valueOf(accountId)});
            try {
                while (cursor.moveToNext()) {
                    Entry entry = new Entry();
                    entry.id = cursor.getLong(0);
                    entry.type = cursor.getString(1);
                    entry.date = cursor.getString(2);
                    entry.title = cursor.getString(3);
                    entry.amount = cursor.getDouble(4);
                    entry.category = cursor.getString(5);
                    entry.subcategory = safe(cursor.getString(6));
                    entry.accountName = cursor.getString(7);
                    entry.notes = safe(cursor.getString(8));
                    entry.billUri = safe(cursor.getString(9));
                    entry.taxFlag = cursor.getInt(10) == 1;
                    entry.taxAmount = cursor.getDouble(11);
                    entry.taxTitle = safe(cursor.getString(12));
                    entry.accountId = cursor.getLong(13);
                    entry.repeatInterval = cursor.getInt(14);
                    entry.repeatUnit = safe(cursor.getString(15));
                    entry.repeatCount = cursor.getInt(16);
                    entry.sourceType = safe(cursor.getString(17));
                    result.add(entry);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        List<Category> categories(long countryId) {
            return categories(countryId, "");
        }

        List<Category> categories(long countryId, String type) {
            List<Category> result = new ArrayList<Category>();
            String sql = "SELECT id, country_id, name, icon, type, subcategories FROM categories " +
                    "WHERE (country_id = ? OR (country_id = 0 AND NOT EXISTS (" +
                    "SELECT 1 FROM category_removals removed WHERE removed.country_id = ? " +
                    "AND LOWER(TRIM(removed.name)) = LOWER(TRIM(categories.name)) " +
                    "AND removed.type = categories.type)))";
            List<String> args = new ArrayList<String>();
            args.add(String.valueOf(countryId));
            args.add(String.valueOf(countryId));
            if (type != null && type.length() > 0) {
                sql += " AND type = ?";
                args.add(type);
            }
            sql += " ORDER BY country_id, name";
            Cursor cursor = getReadableDatabase().rawQuery(sql, args.toArray(new String[args.size()]));
            try {
                while (cursor.moveToNext()) {
                    Category category = new Category();
                    category.id = cursor.getLong(0);
                    category.countryId = cursor.getLong(1);
                    category.name = cursor.getString(2);
                    category.icon = cursor.getString(3);
                    category.type = safe(cursor.getString(4));
                    category.subcategories = safe(cursor.getString(5));
                    result.add(category);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        boolean addCategory(long countryId, String name, String icon, String type) {
            SQLiteDatabase db = getWritableDatabase();
            String cleaned = safe(name).trim();
            if (cleaned.length() == 0) return false;
            if (countInDb(db, "SELECT COUNT(*) FROM categories WHERE country_id = 0 AND LOWER(TRIM(name)) = LOWER(TRIM(?)) AND type = ?",
                    cleaned, type) > 0) {
                db.delete("category_removals", "country_id = ? AND LOWER(TRIM(name)) = LOWER(TRIM(?)) AND type = ?",
                        new String[]{String.valueOf(countryId), cleaned, type});
                return false;
            }
            Cursor cursor = db.rawQuery(
                    "SELECT id FROM categories WHERE country_id = ? AND LOWER(TRIM(name)) = LOWER(TRIM(?)) AND type = ? LIMIT 1",
                    new String[]{String.valueOf(countryId), cleaned, type});
            try {
                if (cursor.moveToFirst()) {
                    ContentValues existing = new ContentValues();
                    existing.put("icon", icon);
                    db.update("categories", existing, "id = ?", new String[]{String.valueOf(cursor.getLong(0))});
                    return false;
                }
            } finally {
                cursor.close();
            }
            ContentValues values = new ContentValues();
            values.put("country_id", countryId);
            values.put("name", cleaned);
            values.put("icon", icon);
            values.put("type", type);
            values.put("subcategories", "");
            db.insert("categories", null, values);
            cleanupDuplicateCategories(db);
            return true;
        }

        void removeCategory(long countryId, Category category) {
            SQLiteDatabase db = getWritableDatabase();
            if (category.countryId == 0) {
                if (countInDb(db, "SELECT COUNT(*) FROM category_removals WHERE country_id = ? AND LOWER(TRIM(name)) = LOWER(TRIM(?)) AND type = ?",
                        String.valueOf(countryId), category.name, category.type) == 0) {
                    ContentValues values = new ContentValues();
                    values.put("country_id", countryId);
                    values.put("name", category.name);
                    values.put("type", category.type);
                    values.put("created_at", now());
                    db.insert("category_removals", null, values);
                }
            } else {
                db.delete("categories", "id = ?", new String[]{String.valueOf(category.id)});
            }
            cleanupDuplicateCategories(db);
        }

        boolean updateCategoryForCountry(long countryId, Category existing, String name, String icon, String type) {
            if (existing == null) return addCategory(countryId, name, icon, type);
            if (existing.countryId == 0) {
                removeCategory(countryId, existing);
                return addCategory(countryId, name, icon, type);
            }
            ContentValues values = new ContentValues();
            values.put("name", name.trim());
            values.put("icon", icon);
            values.put("type", type);
            getWritableDatabase().update("categories", values, "id = ?", new String[]{String.valueOf(existing.id)});
            return true;
        }

        String categoryIcon(long countryId, String categoryName) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT icon FROM categories WHERE name = ? AND (country_id = ? OR country_id = 0) ORDER BY country_id DESC LIMIT 1",
                    new String[]{categoryName, String.valueOf(countryId)});
            try {
                if (cursor.moveToFirst()) return cursor.getString(0);
            } finally {
                cursor.close();
            }
            return "▣";
        }

        String lastCategoryName(long countryId, String type) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT name FROM categories WHERE country_id = ? AND type = ? ORDER BY id DESC LIMIT 1",
                    new String[]{String.valueOf(countryId), type});
            try {
                if (cursor.moveToFirst()) return cursor.getString(0);
            } finally {
                cursor.close();
            }
            return "";
        }

        String[] previousTitles(long countryId, String type) {
            List<String> titles = new ArrayList<String>();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT DISTINCT title FROM entries WHERE country_id = ? AND type = ? ORDER BY date DESC, id DESC LIMIT 80",
                    new String[]{String.valueOf(countryId), type});
            try {
                while (cursor.moveToNext()) titles.add(cursor.getString(0));
            } finally {
                cursor.close();
            }
            return titles.toArray(new String[titles.size()]);
        }

        EntryTemplate templateForTitle(long countryId, String type, String title) {
            EntryTemplate template = new EntryTemplate();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT category, amount, notes FROM entries WHERE country_id = ? AND type = ? AND title = ? ORDER BY date DESC, id DESC LIMIT 1",
                    new String[]{String.valueOf(countryId), type, title});
            try {
                if (cursor.moveToFirst()) {
                    template.category = safe(cursor.getString(0));
                    template.amount = cursor.getDouble(1);
                    template.notes = safe(cursor.getString(2));
                }
            } finally {
                cursor.close();
            }
            return template;
        }

        void addEntry(long countryId, String type, String date, String title, double amount,
                      String category, String subcategory, long accountId, String notes,
                      String billUri, boolean taxFlag, double taxAmount, String taxTitle,
                      int repeatInterval, String repeatUnit, int repeatCount) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                insertEntry(db, countryId, type, date, title, amount, category, subcategory, accountId,
                        notes, billUri, taxFlag, taxAmount, taxTitle, repeatInterval, repeatUnit, repeatCount,
                        "", 0, true);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void updateEntry(long id, String type, String date, String title, double amount,
                         String category, String subcategory, long accountId, String notes,
                         String billUri, boolean taxFlag, double taxAmount, String taxTitle,
                         int repeatInterval, String repeatUnit, int repeatCount) {
            SQLiteDatabase db = getWritableDatabase();
            Cursor cursor = db.rawQuery("SELECT type, amount, account_id, affects_account FROM entries WHERE id = ?",
                    new String[]{String.valueOf(id)});
            db.beginTransaction();
            try {
                boolean affectsAccount = true;
                if (cursor.moveToFirst()) {
                    String oldType = cursor.getString(0);
                    double oldAmount = cursor.getDouble(1);
                    long oldAccountId = cursor.getLong(2);
                    affectsAccount = cursor.isNull(3) || cursor.getInt(3) == 1;
                    if (affectsAccount) adjustAccount(db, oldAccountId, "Income".equals(oldType) ? -oldAmount : oldAmount);
                }
                ContentValues values = new ContentValues();
                values.put("type", type);
                values.put("date", date);
                values.put("title", title);
                values.put("amount", amount);
                values.put("category", category);
                values.put("subcategory", subcategory);
                values.put("account_id", accountId);
                values.put("notes", notes);
                values.put("bill_uri", billUri);
                values.put("tax_flag", taxFlag ? 1 : 0);
                values.put("tax_amount", taxAmount);
                values.put("tax_title", taxTitle);
                values.put("repeat_interval", repeatInterval);
                values.put("repeat_unit", repeatUnit);
                values.put("repeat_count", repeatCount);
                db.update("entries", values, "id = ?", new String[]{String.valueOf(id)});
                if (affectsAccount) adjustAccount(db, accountId, "Income".equals(type) ? amount : -amount);
                db.setTransactionSuccessful();
            } finally {
                cursor.close();
                db.endTransaction();
            }
        }

        private long insertEntry(SQLiteDatabase db, long countryId, String type, String date, String title, double amount,
                                 String category, String subcategory, long accountId, String notes,
                                 String billUri, boolean taxFlag, double taxAmount, String taxTitle,
                                 int repeatInterval, String repeatUnit, int repeatCount,
                                 String sourceType, long sourceId, boolean affectsAccount) {
            ContentValues values = new ContentValues();
            values.put("country_id", countryId);
            values.put("type", type);
            values.put("date", date);
            values.put("title", title);
            values.put("amount", amount);
            values.put("category", category);
            values.put("subcategory", subcategory);
            values.put("account_id", accountId);
            values.put("notes", notes);
            values.put("bill_uri", billUri);
            values.put("tax_flag", taxFlag ? 1 : 0);
            values.put("tax_amount", taxAmount);
            values.put("tax_title", taxTitle);
            values.put("repeat_interval", repeatInterval);
            values.put("repeat_unit", repeatUnit);
            values.put("repeat_count", repeatCount);
            values.put("source_type", sourceType);
            values.put("source_id", sourceId);
            values.put("affects_account", affectsAccount ? 1 : 0);
            values.put("created_at", now());
            long id = db.insert("entries", null, values);
            if (affectsAccount) adjustAccount(db, accountId, "Income".equals(type) ? amount : -amount);
            return id;
        }

        List<Entry> entries(long countryId) {
            return entriesSince(countryId, "");
        }

        boolean recurringOccurrenceExists(long countryId, Entry template, String date) {
            String sql = "SELECT id FROM entries WHERE country_id = ? AND type = ? AND date = ? " +
                    "AND account_id = ? AND id != ? AND repeat_interval = 0 " +
                    "AND (source_type IS NULL OR source_type = '') LIMIT 1";
            Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{
                    String.valueOf(countryId), safe(template.type), date,
                    String.valueOf(template.accountId), String.valueOf(template.id)
            });
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }

        List<Entry> entriesSince(long countryId, String sinceDate) {
            List<Entry> result = new ArrayList<Entry>();
            String sql =
                    "SELECT e.id, e.type, e.date, e.title, e.amount, e.category, e.subcategory, " +
                            "COALESCE(a.name, 'Unknown account'), e.notes, e.bill_uri, e.tax_flag, e.tax_amount, e.tax_title, e.account_id, e.repeat_interval, e.repeat_unit, e.repeat_count, e.source_type " +
                            "FROM entries e LEFT JOIN accounts a ON a.id = e.account_id " +
                            "WHERE e.country_id = ?";
            List<String> args = new ArrayList<String>();
            args.add(String.valueOf(countryId));
            if (sinceDate != null && sinceDate.length() > 0) {
                sql += " AND e.date >= ?";
                args.add(sinceDate);
            }
            sql += " ORDER BY e.date DESC, e.id DESC";
            Cursor cursor = getReadableDatabase().rawQuery(sql, args.toArray(new String[args.size()]));
            try {
                while (cursor.moveToNext()) {
                    Entry entry = new Entry();
                    entry.id = cursor.getLong(0);
                    entry.type = cursor.getString(1);
                    entry.date = cursor.getString(2);
                    entry.title = cursor.getString(3);
                    entry.amount = cursor.getDouble(4);
                    entry.category = cursor.getString(5);
                    entry.subcategory = safe(cursor.getString(6));
                    entry.accountName = cursor.getString(7);
                    entry.notes = safe(cursor.getString(8));
                    entry.billUri = safe(cursor.getString(9));
                    entry.taxFlag = cursor.getInt(10) == 1;
                    entry.taxAmount = cursor.getDouble(11);
                    entry.taxTitle = safe(cursor.getString(12));
                    entry.accountId = cursor.getLong(13);
                    entry.repeatInterval = cursor.getInt(14);
                    entry.repeatUnit = safe(cursor.getString(15));
                    entry.repeatCount = cursor.getInt(16);
                    entry.sourceType = safe(cursor.getString(17));
                    result.add(entry);
                }
            } finally {
                cursor.close();
            }
            appendMissingTransferEntries(result, countryId, sinceDate);
            Collections.sort(result, new Comparator<Entry>() {
                @Override
                public int compare(Entry left, Entry right) {
                    int date = safe(right.date).compareTo(safe(left.date));
                    if (date != 0) return date;
                    return Long.compare(right.id, left.id);
                }
            });
            return result;
        }

        private void appendMissingTransferEntries(List<Entry> result, long countryId, String sinceDate) {
            appendMissingTransferSide(result, countryId, sinceDate, true);
            appendMissingTransferSide(result, countryId, sinceDate, false);
        }

        private void appendMissingTransferSide(List<Entry> result, long countryId, String sinceDate, boolean income) {
            String sourceType = income ? SOURCE_TRANSFER_IN : SOURCE_TRANSFER_OUT;
            String countryColumn = income ? "to_country_id" : "from_country_id";
            String accountColumn = income ? "to_account_id" : "from_account_id";
            String amountColumn = income ? "to_amount" : "from_amount";
            String title = income ? "Transfer from source account" : "Transfer to receiving account";
            String type = income ? "Income" : "Expense";
            List<String> args = new ArrayList<String>();
            args.add(sourceType);
            args.add(String.valueOf(countryId));
            String sql = "SELECT t.id, t.date, t." + amountColumn + ", t." + accountColumn + ", COALESCE(a.name, 'Transfer account') " +
                    "FROM transfers t LEFT JOIN entries e ON e.source_type = ? AND e.source_id = t.id " +
                    "LEFT JOIN accounts a ON a.id = t." + accountColumn + " " +
                    "WHERE t." + countryColumn + " = ? AND e.id IS NULL";
            if (sinceDate != null && sinceDate.length() > 0) {
                sql += " AND t.date >= ?";
                args.add(sinceDate);
            }
            Cursor cursor = getReadableDatabase().rawQuery(sql, args.toArray(new String[args.size()]));
            try {
                while (cursor.moveToNext()) {
                    Entry entry = new Entry();
                    long transferId = cursor.getLong(0);
                    entry.id = -((transferId * 10) + (income ? 1 : 2));
                    entry.type = type;
                    entry.date = cursor.getString(1);
                    entry.title = title;
                    entry.amount = cursor.getDouble(2);
                    entry.category = "Transfers";
                    entry.subcategory = "";
                    entry.accountId = cursor.getLong(3);
                    entry.accountName = cursor.getString(4);
                    entry.notes = "Auto-added from currency transfer";
                    entry.billUri = "";
                    entry.taxFlag = false;
                    entry.taxAmount = 0;
                    entry.taxTitle = "";
                    entry.repeatInterval = 0;
                    entry.repeatUnit = "";
                    entry.repeatCount = 0;
                    entry.sourceType = sourceType;
                    result.add(entry);
                }
            } finally {
                cursor.close();
            }
        }

        void deleteEntry(long id) {
            SQLiteDatabase db = getWritableDatabase();
            Cursor cursor = db.rawQuery("SELECT type, amount, account_id, affects_account FROM entries WHERE id = ?", new String[]{String.valueOf(id)});
            db.beginTransaction();
            try {
                if (cursor.moveToFirst()) {
                    String type = cursor.getString(0);
                    double amount = cursor.getDouble(1);
                    long accountId = cursor.getLong(2);
                    boolean affectsAccount = cursor.isNull(3) || cursor.getInt(3) == 1;
                    if (affectsAccount) adjustAccount(db, accountId, "Income".equals(type) ? -amount : amount);
                }
                db.delete("entries", "id = ?", new String[]{String.valueOf(id)});
                db.setTransactionSuccessful();
            } finally {
                cursor.close();
                db.endTransaction();
            }
        }

        double[] summary(long countryId) {
            double income = sum("SELECT SUM(amount) FROM entries WHERE country_id = ? AND type = 'Income'", String.valueOf(countryId));
            double expenses = sum("SELECT SUM(amount) FROM entries WHERE country_id = ? AND type = 'Expense'", String.valueOf(countryId));
            income += missingTransferTotal(countryId, true, "", "");
            expenses += missingTransferTotal(countryId, false, "", "");
            return new double[]{income, expenses, income - expenses};
        }

        double[] summaryForPeriod(long countryId, String start, String end) {
            double income = sum("SELECT SUM(amount) FROM entries WHERE country_id = ? AND type = 'Income' AND date >= ? AND date <= ?",
                    String.valueOf(countryId), start, end);
            double expenses = sum("SELECT SUM(amount) FROM entries WHERE country_id = ? AND type = 'Expense' AND date >= ? AND date <= ?",
                    String.valueOf(countryId), start, end);
            income += missingTransferTotal(countryId, true, start, end);
            expenses += missingTransferTotal(countryId, false, start, end);
            return new double[]{income, expenses, income - expenses};
        }

        private double missingTransferTotal(long countryId, boolean income, String start, String end) {
            String sourceType = income ? SOURCE_TRANSFER_IN : SOURCE_TRANSFER_OUT;
            String countryColumn = income ? "to_country_id" : "from_country_id";
            String amountColumn = income ? "to_amount" : "from_amount";
            List<String> args = new ArrayList<String>();
            args.add(sourceType);
            args.add(String.valueOf(countryId));
            String sql = "SELECT SUM(t." + amountColumn + ") FROM transfers t " +
                    "LEFT JOIN entries e ON e.source_type = ? AND e.source_id = t.id " +
                    "WHERE t." + countryColumn + " = ? AND e.id IS NULL";
            if (start != null && start.length() > 0) {
                sql += " AND t.date >= ?";
                args.add(start);
            }
            if (end != null && end.length() > 0) {
                sql += " AND t.date <= ?";
                args.add(end);
            }
            return sum(sql, args.toArray(new String[args.size()]));
        }

        double taxTotal(long countryId) {
            return sum("SELECT SUM(tax_amount) FROM entries WHERE country_id = ? AND tax_flag = 1", String.valueOf(countryId));
        }

        double taxTotalForPeriod(long countryId, String start, String end) {
            return sum("SELECT SUM(tax_amount) FROM entries WHERE country_id = ? AND tax_flag = 1 AND date >= ? AND date <= ?",
                    String.valueOf(countryId), start, end);
        }

        double taxTotalForPeriod(long countryId, String type, String start, String end) {
            return sum("SELECT SUM(tax_amount) FROM entries WHERE country_id = ? AND type = ? AND tax_flag = 1 AND date >= ? AND date <= ?",
                    String.valueOf(countryId), type, start, end);
        }

        void addInvestment(long countryId, String type, String title, long accountId, double principal,
                           double presentValue, double maturityValue, String startDate, String maturityDate,
                           String maturityAction, String notes) {
            addInvestment(countryId, type, title, accountId, principal, presentValue, maturityValue,
                    startDate, maturityDate, maturityAction, notes, true);
        }

        void addInvestment(long countryId, String type, String title, long accountId, double principal,
                           double presentValue, double maturityValue, String startDate, String maturityDate,
                           String maturityAction, String notes, boolean affectsAccount) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                String storedNotes = notes == null ? "" : notes;
                if (!affectsAccount && !storedNotes.contains("Created from maturity reinvestment")) {
                    storedNotes = storedNotes.length() == 0
                            ? "Created from maturity reinvestment"
                            : storedNotes + "\nCreated from maturity reinvestment";
                }
                ContentValues values = new ContentValues();
                values.put("country_id", countryId);
                values.put("type", type);
                values.put("title", title);
                values.put("account_id", accountId);
                values.put("principal", principal);
                values.put("current_value", presentValue);
                values.put("annual_rate", 0);
                values.put("start_date", startDate);
                values.put("maturity_date", maturityDate);
                values.put("maturity_value", maturityValue);
                values.put("maturity_action", maturityAction);
                values.put("status", "Active");
                values.put("notes", storedNotes);
                values.put("created_at", now());
                long investmentId = db.insert("investments", null, values);
                insertEntry(db, countryId, "Expense", startDate, title + " opened", principal,
                        "Investments", "", accountId, "Auto-added from investment opening", "",
                        false, 0, "", 0, "", 0, SOURCE_INVESTMENT_OPEN, investmentId, affectsAccount);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        List<Investment> investments(long countryId) {
            List<Investment> result = new ArrayList<Investment>();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT i.id, i.type, i.title, i.account_id, COALESCE(a.name, 'No linked account'), " +
                            "i.principal, i.current_value, i.annual_rate, i.start_date, i.maturity_date, i.maturity_value, i.maturity_action, i.status, i.notes " +
                            "FROM investments i LEFT JOIN accounts a ON a.id = i.account_id " +
                            "WHERE i.country_id = ? ORDER BY i.type, i.title",
                    new String[]{String.valueOf(countryId)});
            try {
                while (cursor.moveToNext()) {
                    Investment investment = new Investment();
                    investment.id = cursor.getLong(0);
                    investment.type = cursor.getString(1);
                    investment.title = cursor.getString(2);
                    investment.accountId = cursor.getLong(3);
                    investment.accountName = cursor.getString(4);
                    investment.principal = cursor.getDouble(5);
                    investment.currentValue = cursor.getDouble(6);
                    investment.annualRate = cursor.getDouble(7);
                    investment.startDate = cursor.getString(8);
                    investment.maturityDate = safe(cursor.getString(9));
                    investment.maturityValue = cursor.getDouble(10);
                    investment.maturityAction = safe(cursor.getString(11));
                    if (investment.maturityAction.length() == 0) investment.maturityAction = "Return full maturity amount to linked account";
                    investment.status = safe(cursor.getString(12));
                    if (investment.status.length() == 0) investment.status = "Active";
                    investment.notes = safe(cursor.getString(13));
                    result.add(investment);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        double[] investmentSummary(long countryId) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT principal, current_value, annual_rate, start_date, type FROM investments WHERE country_id = ?",
                    new String[]{String.valueOf(countryId)});
            double principal = 0;
            double value = 0;
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            try {
                while (cursor.moveToNext()) {
                    double itemPrincipal = cursor.getDouble(0);
                    double current = cursor.getDouble(1);
                    double rate = cursor.getDouble(2);
                    String start = cursor.getString(3);
                    String type = cursor.getString(4);
                    principal += itemPrincipal;
                    if (current > 0) {
                        value += current;
                    } else {
                        double years = 0;
                        try {
                            Date s = formatter.parse(start);
                            Date e = formatter.parse(today);
                            if (s != null && e != null) years = Math.max(0, (e.getTime() - s.getTime()) / 86400000.0 / 365.0);
                        } catch (Exception ignored) {
                        }
                        if ("Recurring Deposit".equals(type)) years = Math.max(0.1, years / 2.0);
                        value += itemPrincipal * (1.0 + (rate / 100.0) * years);
                    }
                }
            } finally {
                cursor.close();
            }
            return new double[]{principal, value};
        }

        double[] investedByType(long countryId) {
            double fd = sum("SELECT SUM(principal) FROM investments WHERE country_id = ? AND type = 'Fixed Deposit'", String.valueOf(countryId));
            double rd = sum("SELECT SUM(principal) FROM investments WHERE country_id = ? AND type = 'Recurring Deposit'", String.valueOf(countryId));
            double mf = sum("SELECT SUM(principal) FROM investments WHERE country_id = ? AND type = 'Market Linked Fund'", String.valueOf(countryId));
            return new double[]{fd, rd, mf};
        }

        void updateInvestmentValue(long investmentId, double presentValue) {
            ContentValues values = new ContentValues();
            values.put("current_value", presentValue);
            getWritableDatabase().update("investments", values, "id = ?", new String[]{String.valueOf(investmentId)});
        }

        void updateInvestmentValueWithGain(Investment investment, long countryId, String date, double presentValue, double gain) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put("current_value", presentValue);
                db.update("investments", values, "id = ?", new String[]{String.valueOf(investment.id)});
                if (gain > 0) {
                    insertEntry(db, countryId, "Income", date, investment.title + " capital gains", gain,
                            "Investments", "", investment.accountId, "Auto-added from investment update", "",
                            false, 0, "", 0, "", 0, SOURCE_INVESTMENT_GAIN, investment.id, true);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void updateInvestmentAmounts(Investment investment, double principal, double presentValue, double maturityValue) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                double principalDelta = principal - investment.principal;
                ContentValues values = new ContentValues();
                values.put("principal", principal);
                values.put("current_value", presentValue);
                values.put("maturity_value", maturityValue);
                db.update("investments", values, "id = ?", new String[]{String.valueOf(investment.id)});
                if (Math.abs(principalDelta) > 0.0001) {
                    adjustAccount(db, investment.accountId, -principalDelta);
                    ContentValues entry = new ContentValues();
                    entry.put("amount", principal);
                    db.update("entries", entry, "source_type = ? AND source_id = ?",
                            new String[]{SOURCE_INVESTMENT_OPEN, String.valueOf(investment.id)});
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void addInvestmentFunds(Investment investment, long countryId, String date, double amount, double maturityValue, String notes) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put("principal", investment.principal + amount);
                values.put("current_value", investment.currentValue + amount);
                values.put("maturity_value", maturityValue);
                db.update("investments", values, "id = ?", new String[]{String.valueOf(investment.id)});
                String entryNotes = notes == null || notes.trim().length() == 0
                        ? "Auto-added from investment additional funds" : notes.trim();
                insertEntry(db, countryId, "Expense", date, investment.title + " added funds", amount,
                        "Investments", "", investment.accountId,
                        entryNotes, "",
                        false, 0, "", 0, "", 0, SOURCE_INVESTMENT_ADD, investment.id, true);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void deleteInvestment(Investment investment) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                double reversedOutflow = deleteSourceEntries(db, investment.id);
                if (!safe(investment.notes).contains("Created from maturity reinvestment") && reversedOutflow < investment.principal) {
                    adjustAccount(db, investment.accountId, investment.principal - reversedOutflow);
                }
                db.delete("investments", "id = ?", new String[]{String.valueOf(investment.id)});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private double deleteSourceEntries(SQLiteDatabase db, long investmentId) {
            Cursor cursor = db.rawQuery(
                    "SELECT id, type, amount, account_id, affects_account FROM entries WHERE source_id = ? AND source_type IN (?,?,?,?)",
                    new String[]{String.valueOf(investmentId), SOURCE_INVESTMENT_OPEN, SOURCE_INVESTMENT_ADD,
                            SOURCE_INVESTMENT_GAIN, SOURCE_INVESTMENT_MATURITY});
            List<Long> ids = new ArrayList<Long>();
            double reversedOutflow = 0;
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String type = cursor.getString(1);
                    double amount = cursor.getDouble(2);
                    long accountId = cursor.getLong(3);
                    boolean affectsAccount = cursor.isNull(4) || cursor.getInt(4) == 1;
                    if (affectsAccount) {
                        adjustAccount(db, accountId, "Income".equals(type) ? -amount : amount);
                        if ("Expense".equals(type)) reversedOutflow += amount;
                    }
                    ids.add(id);
                }
            } finally {
                cursor.close();
            }
            for (Long id : ids) {
                db.delete("entries", "id = ?", new String[]{String.valueOf(id)});
            }
            return reversedOutflow;
        }

        void processEarlyInvestmentClose(Investment investment, String date, double returnedAmount) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long countryId = investmentCountryId(db, investment.id);
                insertEntry(db, countryId, "Income", date,
                        investment.title + " closure return", returnedAmount,
                        "Investments", "", investment.accountId,
                        "Auto-added from investment closure", "", false, 0, "", 0, "", 0,
                        SOURCE_INVESTMENT_MATURITY, investment.id, true);
                ContentValues values = new ContentValues();
                values.put("current_value", returnedAmount);
                values.put("maturity_value", returnedAmount);
                values.put("maturity_action", "Closed before maturity");
                values.put("status", "Matured");
                db.update("investments", values, "id = ?", new String[]{String.valueOf(investment.id)});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void processMaturityForReinvestmentChoice(Investment investment, String date, double maturityValue,
                                                  String action, double returnedAmount) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long countryId = investmentCountryId(db, investment.id);
                if (returnedAmount > 0) {
                    insertEntry(db, countryId, "Income", date,
                            investment.title + " maturity interest", returnedAmount,
                            "Interest", "", investment.accountId,
                            "Auto-added from investment maturity interest", "", false, 0, "", 0, "", 0,
                            SOURCE_INVESTMENT_MATURITY, investment.id, true);
                }
                ContentValues values = new ContentValues();
                values.put("current_value", maturityValue);
                values.put("maturity_value", maturityValue);
                values.put("maturity_action", action);
                values.put("status", "Matured");
                db.update("investments", values, "id = ?", new String[]{String.valueOf(investment.id)});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void processMaturity(Investment investment, String date, double maturityValue, String action) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long countryId = investmentCountryId(db, investment.id);
                double interest = Math.max(0, maturityValue - investment.principal);
                if ("Return full maturity amount to linked account".equals(action)) {
                    insertEntry(db, countryId, "Income", date,
                            investment.title + " maturity principal", Math.min(investment.principal, maturityValue),
                            "Investments", "", investment.accountId,
                            "Auto-added from investment maturity principal", "", false, 0, "", 0, "", 0,
                            SOURCE_INVESTMENT_MATURITY, investment.id, true);
                    if (interest > 0) {
                        insertEntry(db, countryId, "Income", date,
                                investment.title + " maturity interest", interest, "Interest", "", investment.accountId,
                                "Auto-added from investment maturity interest", "", false, 0, "", 0, "", 0,
                                SOURCE_INVESTMENT_MATURITY, investment.id, true);
                    }
                } else if ("Reinvest principal only".equals(action)) {
                    if (interest > 0) {
                        insertEntry(db, countryId, "Income", date,
                                investment.title + " maturity interest", interest, "Interest", "", investment.accountId,
                                "Auto-added from investment maturity interest", "", false, 0, "", 0, "", 0,
                                SOURCE_INVESTMENT_MATURITY, investment.id, true);
                    }
                    createReinvestment(db, investment, investment.principal, date, action);
                } else {
                    createReinvestment(db, investment, maturityValue, date, action);
                }
                ContentValues values = new ContentValues();
                values.put("current_value", maturityValue);
                values.put("maturity_value", maturityValue);
                values.put("maturity_action", action);
                values.put("status", "Matured");
                db.update("investments", values, "id = ?", new String[]{String.valueOf(investment.id)});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void processMarketFundMaturity(Investment investment, long countryId, String date, double maturityValue, long accountId) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                double principal = Math.min(investment.principal, maturityValue);
                double gain = Math.max(0, maturityValue - investment.principal);
                insertEntry(db, countryId, "Income", date, investment.title + " redemption principal", principal,
                        "Investments", "", accountId, "Auto-added from market fund redemption", "",
                        false, 0, "", 0, "", 0, SOURCE_INVESTMENT_MATURITY, investment.id, true);
                if (gain > 0) {
                    insertEntry(db, countryId, "Income", date, investment.title + " redemption gains", gain,
                            "Investments", "", accountId, "Auto-added from market fund redemption", "",
                            false, 0, "", 0, "", 0, SOURCE_INVESTMENT_MATURITY, investment.id, true);
                }
                ContentValues values = new ContentValues();
                values.put("account_id", accountId);
                values.put("current_value", maturityValue);
                values.put("maturity_value", maturityValue);
                values.put("status", "Matured");
                db.update("investments", values, "id = ?", new String[]{String.valueOf(investment.id)});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private long investmentCountryId(SQLiteDatabase db, long investmentId) {
            Cursor cursor = db.rawQuery("SELECT country_id FROM investments WHERE id = ?", new String[]{String.valueOf(investmentId)});
            try {
                if (cursor.moveToFirst()) return cursor.getLong(0);
            } finally {
                cursor.close();
            }
            return 0;
        }

        private void createReinvestment(SQLiteDatabase db, Investment investment, double principal, String date, String action) {
            ContentValues values = new ContentValues();
            values.put("country_id", investmentCountryId(db, investment.id));
            values.put("type", investment.type);
            values.put("title", investment.title + " reinvested");
            values.put("account_id", investment.accountId);
            values.put("principal", principal);
            values.put("current_value", principal);
            values.put("annual_rate", 0);
            values.put("start_date", date);
            values.put("maturity_date", "");
            values.put("maturity_value", principal);
            values.put("maturity_action", action);
            values.put("status", "Active");
            values.put("notes", "Created from maturity reinvestment");
            values.put("created_at", now());
            db.insert("investments", null, values);
        }

        void addGoal(long countryId, String title, double targetAmount, double currentAmount, String targetDate, long accountId, String notes) {
            ContentValues values = new ContentValues();
            values.put("country_id", countryId);
            values.put("account_id", accountId);
            values.put("title", title);
            values.put("target_amount", targetAmount);
            values.put("current_amount", currentAmount);
            values.put("target_date", targetDate);
            values.put("notes", notes);
            values.put("created_at", now());
            getWritableDatabase().insert("goals", null, values);
        }

        List<Goal> goals(long countryId) {
            List<Goal> result = new ArrayList<Goal>();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT g.id, g.account_id, COALESCE(a.name, ''), g.title, g.target_amount, g.current_amount, g.target_date, g.notes " +
                            "FROM goals g LEFT JOIN accounts a ON a.id = g.account_id WHERE g.country_id = ? ORDER BY g.id DESC",
                    new String[]{String.valueOf(countryId)});
            try {
                while (cursor.moveToNext()) {
                    Goal goal = new Goal();
                    goal.id = cursor.getLong(0);
                    goal.accountId = cursor.getLong(1);
                    goal.accountName = safe(cursor.getString(2));
                    goal.title = cursor.getString(3);
                    goal.targetAmount = cursor.getDouble(4);
                    goal.currentAmount = cursor.getDouble(5);
                    goal.targetDate = safe(cursor.getString(6));
                    goal.notes = safe(cursor.getString(7));
                    result.add(goal);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        void updateGoalCurrent(long goalId, double currentAmount, long accountId) {
            ContentValues values = new ContentValues();
            values.put("current_amount", currentAmount);
            if (accountId > 0) values.put("account_id", accountId);
            getWritableDatabase().update("goals", values, "id = ?", new String[]{String.valueOf(goalId)});
        }

        void updateGoal(long goalId, String title, double targetAmount, double currentAmount, String targetDate, long accountId, String notes) {
            ContentValues values = new ContentValues();
            values.put("title", title);
            values.put("target_amount", targetAmount);
            values.put("current_amount", currentAmount);
            values.put("target_date", targetDate);
            values.put("account_id", accountId);
            values.put("notes", notes);
            getWritableDatabase().update("goals", values, "id = ?", new String[]{String.valueOf(goalId)});
        }

        void addBudget(long countryId, String name, String category, String subcategory, String month,
                       double limitAmount, String notes, boolean recurring) {
            ContentValues values = new ContentValues();
            values.put("country_id", countryId);
            values.put("name", name);
            values.put("category", category);
            values.put("subcategory", subcategory);
            values.put("month", month);
            values.put("limit_amount", limitAmount);
            values.put("notes", notes);
            values.put("is_recurring", recurring ? 1 : 0);
            values.put("created_at", now());
            getWritableDatabase().insert("budgets", null, values);
        }

        void updateBudget(long id, String name, String category, String subcategory, String month,
                          double limitAmount, String notes, boolean recurring) {
            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("category", category);
            values.put("subcategory", subcategory);
            values.put("month", month);
            values.put("limit_amount", limitAmount);
            values.put("notes", notes);
            values.put("is_recurring", recurring ? 1 : 0);
            getWritableDatabase().update("budgets", values, "id = ?", new String[]{String.valueOf(id)});
        }

        List<BudgetProgress> budgetProgress(long countryId, String onlyMonth) {
            List<BudgetProgress> result = new ArrayList<BudgetProgress>();
            String sql = "SELECT id, name, category, subcategory, month, limit_amount, notes, is_recurring FROM budgets WHERE country_id = ?";
            List<String> args = new ArrayList<String>();
            args.add(String.valueOf(countryId));
            if (onlyMonth != null) {
                sql += " AND (month = ? OR (is_recurring = 1 AND month <= ?))";
                args.add(onlyMonth);
                args.add(onlyMonth);
            }
            sql += " ORDER BY month DESC, name, category";
            Cursor cursor = getReadableDatabase().rawQuery(sql, args.toArray(new String[args.size()]));
            try {
                while (cursor.moveToNext()) {
                    BudgetProgress budget = new BudgetProgress();
                    budget.id = cursor.getLong(0);
                    budget.name = safe(cursor.getString(1));
                    budget.category = cursor.getString(2);
                    budget.subcategory = safe(cursor.getString(3));
                    budget.month = cursor.getString(4);
                    budget.limit = cursor.getDouble(5);
                    budget.notes = safe(cursor.getString(6));
                    budget.recurring = cursor.getInt(7) == 1;
                    if (budget.name.length() == 0) budget.name = budget.category;
                    budget.spent = spentForBudget(countryId, budget.category, budget.subcategory,
                            onlyMonth == null ? budget.month : onlyMonth);
                    result.add(budget);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        double dailyRate(String fromCurrency, String toCurrency, String date) {
            if (fromCurrency.equals(toCurrency)) return 1;
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT rate FROM exchange_rates WHERE month = ? AND from_currency = ? AND to_currency = ? ORDER BY id DESC LIMIT 1",
                    new String[]{date, fromCurrency, toCurrency});
            try {
                if (cursor.moveToFirst()) return cursor.getDouble(0);
            } finally {
                cursor.close();
            }
            return 0;
        }

        String rateSource(String fromCurrency, String toCurrency, String date) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT source FROM exchange_rates WHERE month = ? AND from_currency = ? AND to_currency = ? ORDER BY id DESC LIMIT 1",
                    new String[]{date, fromCurrency, toCurrency});
            try {
                if (cursor.moveToFirst()) return safe(cursor.getString(0));
            } finally {
                cursor.close();
            }
            return "Manual";
        }

        void upsertRate(String date, String fromCurrency, String toCurrency, double rate, String source) {
            SQLiteDatabase db = getWritableDatabase();
            db.delete("exchange_rates", "month = ? AND from_currency = ? AND to_currency = ?", new String[]{date, fromCurrency, toCurrency});
            ContentValues values = new ContentValues();
            values.put("month", date);
            values.put("from_currency", fromCurrency);
            values.put("to_currency", toCurrency);
            values.put("rate", rate);
            values.put("source", source);
            values.put("updated_at", now());
            db.insert("exchange_rates", null, values);
        }

        List<RateView> rateViewsForCountries(String date) {
            List<Country> countries = getCountries();
            List<RateView> result = new ArrayList<RateView>();
            Set<String> seen = new HashSet<String>();
            for (int i = 0; i < countries.size(); i++) {
                for (int j = i + 1; j < countries.size(); j++) {
                    String from = countries.get(i).currency;
                    String to = countries.get(j).currency;
                    if (from.equals(to)) continue;
                    String key = from + ":" + to;
                    if (seen.contains(key)) continue;
                    seen.add(key);
                    RateView rate = new RateView();
                    rate.fromCurrency = from;
                    rate.toCurrency = to;
                    rate.rate = dailyRate(from, to, date);
                    rate.source = rateSource(from, to, date);
                    rate.date = date;
                    result.add(rate);
                }
            }
            return result;
        }

        void addTransfer(String date, long fromCountryId, long toCountryId, long fromAccountId, long toAccountId,
                         double fromAmount, double toAmount, double rate, double feeAmount, String rateSource, String notes) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                insertTransfer(db, date, fromCountryId, toCountryId, fromAccountId, toAccountId,
                        fromAmount, toAmount, rate, feeAmount, rateSource, notes);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void updateTransfer(long transferId, String date, long fromCountryId, long toCountryId, long fromAccountId, long toAccountId,
                            double fromAmount, double toAmount, double rate, double feeAmount, String rateSource, String notes) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                reverseTransfer(db, transferId);
                db.delete("transfers", "id = ?", new String[]{String.valueOf(transferId)});
                insertTransfer(db, date, fromCountryId, toCountryId, fromAccountId, toAccountId,
                        fromAmount, toAmount, rate, feeAmount, rateSource, notes);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        void deleteTransfer(long transferId) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                reverseTransfer(db, transferId);
                db.delete("transfers", "id = ?", new String[]{String.valueOf(transferId)});
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        private long insertTransfer(SQLiteDatabase db, String date, long fromCountryId, long toCountryId,
                                    long fromAccountId, long toAccountId, double fromAmount, double toAmount,
                                    double rate, double feeAmount, String rateSource, String notes) {
            ContentValues values = new ContentValues();
            values.put("date", date);
            values.put("from_country_id", fromCountryId);
            values.put("to_country_id", toCountryId);
            values.put("from_account_id", fromAccountId);
            values.put("to_account_id", toAccountId);
            values.put("from_amount", fromAmount);
            values.put("to_amount", toAmount);
            values.put("rate", rate);
            values.put("fee_amount", feeAmount);
            values.put("rate_source", rateSource);
            values.put("notes", notes);
            values.put("created_at", now());
            long transferId = db.insert("transfers", null, values);
            adjustAccount(db, fromAccountId, -(fromAmount + Math.max(0, feeAmount)));
            adjustAccount(db, toAccountId, toAmount);
            insertEntry(db, fromCountryId, "Expense", date, "Transfer to receiving account", fromAmount,
                    "Transfers", "", fromAccountId, "Auto-added from currency transfer", "",
                    false, 0, "", 0, "", 0, SOURCE_TRANSFER_OUT, transferId, false);
            insertEntry(db, toCountryId, "Income", date, "Transfer from source account", toAmount,
                    "Transfers", "", toAccountId, "Auto-added from currency transfer", "",
                    false, 0, "", 0, "", 0, SOURCE_TRANSFER_IN, transferId, false);
            if (feeAmount > 0) {
                insertEntry(db, fromCountryId, "Expense", date, "Transfer fee", feeAmount,
                        "Fees", "", fromAccountId, "Auto-added from currency transfer", "",
                        false, 0, "", 0, "", 0, SOURCE_TRANSFER_FEE, transferId, false);
            }
            return transferId;
        }

        private void reverseTransfer(SQLiteDatabase db, long transferId) {
            Cursor cursor = db.rawQuery(
                    "SELECT date, from_account_id, to_account_id, from_amount, to_amount, fee_amount FROM transfers WHERE id = ?",
                    new String[]{String.valueOf(transferId)});
            try {
                if (cursor.moveToFirst()) {
                    String date = cursor.getString(0);
                    long fromAccountId = cursor.getLong(1);
                    long toAccountId = cursor.getLong(2);
                    double fromAmount = cursor.getDouble(3);
                    double toAmount = cursor.getDouble(4);
                    double feeAmount = cursor.getDouble(5);
                    adjustAccount(db, fromAccountId, fromAmount + Math.max(0, feeAmount));
                    adjustAccount(db, toAccountId, -toAmount);
                    int deletedLinkedEntries = db.delete("entries", "source_id = ? AND source_type IN (?,?,?)",
                            new String[]{String.valueOf(transferId), SOURCE_TRANSFER_FEE, SOURCE_TRANSFER_OUT, SOURCE_TRANSFER_IN});
                    if (deletedLinkedEntries == 0 && feeAmount > 0) {
                        deleteLegacyTransferFee(db, date, fromAccountId, feeAmount);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        private void deleteLegacyTransferFee(SQLiteDatabase db, String date, long fromAccountId, double feeAmount) {
            Cursor cursor = db.rawQuery(
                    "SELECT id FROM entries WHERE type = 'Expense' AND title = 'Transfer fee' AND date = ? " +
                            "AND account_id = ? AND amount = ? AND notes = 'Auto-added from currency transfer' " +
                            "AND (source_type IS NULL OR source_type = '') ORDER BY id DESC LIMIT 1",
                    new String[]{date, String.valueOf(fromAccountId), String.valueOf(feeAmount)});
            try {
                if (cursor.moveToFirst()) {
                    db.delete("entries", "id = ?", new String[]{String.valueOf(cursor.getLong(0))});
                }
            } finally {
                cursor.close();
            }
        }

        List<Transfer> transfers() {
            List<Transfer> result = new ArrayList<Transfer>();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT t.id, t.from_country_id, t.to_country_id, t.from_account_id, t.to_account_id, " +
                            "t.date, fc.name, tc.name, COALESCE(fa.name, 'Source account'), COALESCE(ta.name, 'Receiving account'), " +
                            "fc.currency, tc.currency, t.from_amount, t.to_amount, t.rate, t.fee_amount, t.rate_source, t.notes " +
                            "FROM transfers t JOIN countries fc ON fc.id = t.from_country_id JOIN countries tc ON tc.id = t.to_country_id " +
                            "LEFT JOIN accounts fa ON fa.id = t.from_account_id LEFT JOIN accounts ta ON ta.id = t.to_account_id " +
                            "ORDER BY t.date DESC, t.id DESC",
                    null);
            try {
                while (cursor.moveToNext()) {
                    Transfer transfer = new Transfer();
                    transfer.id = cursor.getLong(0);
                    transfer.fromCountryId = cursor.getLong(1);
                    transfer.toCountryId = cursor.getLong(2);
                    transfer.fromAccountId = cursor.getLong(3);
                    transfer.toAccountId = cursor.getLong(4);
                    transfer.date = cursor.getString(5);
                    transfer.fromCountry = cursor.getString(6);
                    transfer.toCountry = cursor.getString(7);
                    transfer.fromAccount = cursor.getString(8);
                    transfer.toAccount = cursor.getString(9);
                    transfer.fromCurrency = cursor.getString(10);
                    transfer.toCurrency = cursor.getString(11);
                    transfer.fromAmount = cursor.getDouble(12);
                    transfer.toAmount = cursor.getDouble(13);
                    transfer.rate = cursor.getDouble(14);
                    transfer.feeAmount = cursor.getDouble(15);
                    transfer.rateSource = safe(cursor.getString(16));
                    transfer.notes = safe(cursor.getString(17));
                    result.add(transfer);
                }
            } finally {
                cursor.close();
            }
            return result;
        }

        Transfer latestTransfer() {
            List<Transfer> result = transfers();
            return result.isEmpty() ? null : result.get(0);
        }

        String entryReport(long countryId, String type, String start, String end, String categories, boolean taxOnly, String currency) {
            List<String> args = new ArrayList<String>();
            String sql = "SELECT category, title, date, amount, tax_amount FROM entries WHERE country_id = ? AND date >= ? AND date <= ?";
            args.add(String.valueOf(countryId));
            args.add(start);
            args.add(end);
            if (type.length() > 0) {
                sql += " AND type = ?";
                args.add(type);
            }
            if (taxOnly) {
                sql += " AND tax_flag = 1";
            }
            if (categories != null && categories.length() > 0) {
                String[] parts = categories.split(",");
                StringBuilder placeholders = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) placeholders.append(",");
                    placeholders.append("?");
                    args.add(parts[i].trim());
                }
                sql += " AND category IN (" + placeholders + ")";
            }
            sql += " ORDER BY date DESC, category, title";
            Cursor cursor = getReadableDatabase().rawQuery(sql, args.toArray(new String[args.size()]));
            StringBuilder report = new StringBuilder();
            double total = 0;
            try {
                while (cursor.moveToNext()) {
                    String category = cursor.getString(0);
                    String title = cursor.getString(1);
                    String date = cursor.getString(2);
                    double amount = taxOnly ? cursor.getDouble(4) : cursor.getDouble(3);
                    total += amount;
                    report.append(date).append(" · ").append(category).append(" · ").append(title)
                            .append(" · ").append(money(amount, currency)).append("\n");
                }
            } finally {
                cursor.close();
            }
            if (report.length() == 0) report.append("No matching records.");
            report.insert(0, "Total: " + money(total, currency) + "\n\n");
            return report.toString();
        }

        String investmentReport(long countryId, String start, String end, String currency) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT type, title, start_date, principal, maturity_date, maturity_value FROM investments WHERE country_id = ? AND start_date >= ? AND start_date <= ? ORDER BY start_date DESC",
                    new String[]{String.valueOf(countryId), start, end});
            StringBuilder report = new StringBuilder();
            double total = 0;
            try {
                while (cursor.moveToNext()) {
                    double amount = cursor.getDouble(3);
                    total += amount;
                    report.append(cursor.getString(2)).append(" · ").append(cursor.getString(0)).append(" · ")
                            .append(cursor.getString(1)).append(" · invested ").append(money(amount, currency))
                            .append(" · maturity ").append(safe(cursor.getString(4))).append(" / ")
                            .append(money(cursor.getDouble(5), currency)).append("\n");
                }
            } finally {
                cursor.close();
            }
            if (report.length() == 0) report.append("No matching investments.");
            report.insert(0, "Total invested: " + money(total, currency) + "\n\n");
            return report.toString();
        }

        double selectedAccountTotal(Set<Long> ids) {
            if (ids.isEmpty()) return 0;
            StringBuilder placeholders = new StringBuilder();
            String[] args = new String[ids.size()];
            int i = 0;
            for (Long id : ids) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
                args[i] = String.valueOf(id);
                i++;
            }
            return sum("SELECT SUM(balance) FROM accounts WHERE id IN (" + placeholders + ")", args);
        }

        double selectedGoalReserved(Set<Long> ids) {
            if (ids.isEmpty()) return 0;
            StringBuilder placeholders = new StringBuilder();
            String[] args = new String[ids.size()];
            int i = 0;
            for (Long id : ids) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
                args[i] = String.valueOf(id);
                i++;
            }
            return sum("SELECT SUM(current_amount) FROM goals WHERE account_id IN (" + placeholders + ")", args);
        }

        double goalReservedForAccount(long accountId) {
            return sum("SELECT SUM(current_amount) FROM goals WHERE account_id = ?", String.valueOf(accountId));
        }

        double goalReservedForAccountExcept(long accountId, long goalId) {
            return sum("SELECT SUM(current_amount) FROM goals WHERE account_id = ? AND id != ?",
                    String.valueOf(accountId), String.valueOf(goalId));
        }

        String allCountriesAccountSummary() {
            StringBuilder builder = new StringBuilder();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT currency, SUM(balance) FROM accounts GROUP BY currency ORDER BY currency",
                    null);
            try {
                while (cursor.moveToNext()) {
                    if (builder.length() > 0) builder.append(" · ");
                    builder.append(money(cursor.getDouble(1), cursor.getString(0)));
                }
            } finally {
                cursor.close();
            }
            return builder.length() == 0 ? "No accounts" : builder.toString();
        }

        String otherCountriesAvailableSummary(long countryId) {
            StringBuilder builder = new StringBuilder();
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT a.currency, SUM(a.balance - COALESCE((SELECT SUM(g.current_amount) FROM goals g WHERE g.account_id = a.id), 0)) " +
                            "FROM accounts a WHERE a.country_id != ? GROUP BY a.currency ORDER BY a.currency",
                    new String[]{String.valueOf(countryId)});
            try {
                while (cursor.moveToNext()) {
                    if (builder.length() > 0) builder.append(" · ");
                    builder.append(money(cursor.getDouble(1), cursor.getString(0)));
                }
            } finally {
                cursor.close();
            }
            return builder.toString();
        }

        boolean accountHasActivity(long accountId) {
            return count("SELECT COUNT(*) FROM entries WHERE account_id = ?", String.valueOf(accountId)) > 0
                    || count("SELECT COUNT(*) FROM investments WHERE account_id = ?", String.valueOf(accountId)) > 0
                    || count("SELECT COUNT(*) FROM goals WHERE account_id = ?", String.valueOf(accountId)) > 0
                    || count("SELECT COUNT(*) FROM transfers WHERE from_account_id = ? OR to_account_id = ?", String.valueOf(accountId), String.valueOf(accountId)) > 0;
        }

        void deleteById(String table, long id) {
            getWritableDatabase().delete(table, "id = ?", new String[]{String.valueOf(id)});
        }

        private double spentForBudget(long countryId, String category, String subcategory, String month) {
            List<String> args = new ArrayList<String>();
            String[] categories = category.split(",");
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < categories.length; i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String sql = "SELECT SUM(amount) FROM entries WHERE country_id = ? AND type = 'Expense' AND category IN (" + placeholders + ") AND date LIKE ?";
            args.add(String.valueOf(countryId));
            for (String item : categories) {
                args.add(item.trim());
            }
            args.add(month + "%");
            return sum(sql, args.toArray(new String[args.size()]));
        }

        private static void adjustAccount(SQLiteDatabase db, long accountId, double delta) {
            db.execSQL("UPDATE accounts SET balance = balance + ? WHERE id = ?", new Object[]{delta, accountId});
        }

        private double sum(String sql, String... args) {
            Cursor cursor = getReadableDatabase().rawQuery(sql, args);
            try {
                if (cursor.moveToFirst()) return cursor.isNull(0) ? 0 : cursor.getDouble(0);
            } finally {
                cursor.close();
            }
            return 0;
        }

        private long count(String sql, String... args) {
            Cursor cursor = getReadableDatabase().rawQuery(sql, args);
            try {
                if (cursor.moveToFirst()) return cursor.getLong(0);
            } finally {
                cursor.close();
            }
            return 0;
        }

        private long countInDb(SQLiteDatabase db, String sql, String... args) {
            Cursor cursor = db.rawQuery(sql, args);
            try {
                if (cursor.moveToFirst()) return cursor.getLong(0);
            } finally {
                cursor.close();
            }
            return 0;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }

        private static String now() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        }
    }
}
