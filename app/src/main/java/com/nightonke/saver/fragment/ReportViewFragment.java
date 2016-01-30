package com.nightonke.saver.fragment;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.appeaser.sublimepickerlibrary.helpers.SublimeOptions;
import com.github.aakira.expandablelayout.ExpandableRelativeLayout;
import com.github.florent37.materialviewpager.MaterialViewPagerHelper;
import com.github.johnpersano.supertoasts.SuperToast;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollView;
import com.melnykov.fab.FloatingActionButton;
import com.nightonke.saver.BuildConfig;
import com.nightonke.saver.R;
import com.nightonke.saver.activity.CoCoinApplication;
import com.nightonke.saver.adapter.DialogMonthSelectGridViewAdapter;
import com.nightonke.saver.adapter.DialogSelectListDataAdapter;
import com.nightonke.saver.adapter.ReportTagAdapter;
import com.nightonke.saver.model.CoCoinRecord;
import com.nightonke.saver.model.RecordManager;
import com.nightonke.saver.model.SettingManager;
import com.nightonke.saver.ui.ExpandedListView;
import com.nightonke.saver.ui.MyGridView;
import com.nightonke.saver.util.CoCoinUtil;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.listeners.ActionClickListener;
import com.squareup.leakcanary.RefWatcher;

import net.steamcrafted.materialiconlib.MaterialIconView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PieChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.SliceValue;
import lecho.lib.hellocharts.model.ValueShape;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PieChartView;

/**
 * Created by 伟平 on 2015/10/20.
 *
 * report is to show the expense of some time for user
 *
 * if the user select a year
 * we show
 * expense and sum(compare with last year)
 * the number of tags used in(compare with last year)
 * a pie for all expense on diff tags
 * the highest expense on some tags(and percent), click for more
 * the lowest expense on some tags(and percent), click for more
 * the percent used in food, clothes, house and traffic
 * the highest expense on some tags except the four kinds of tags above
 * a line chart of every month expense
 * the highest expense of some months(and percent), click for more
 * the lowest expense of some months(and percent), click for more
 * the average value of expense of a month
 * the highest expense of some days, click for more(@param MAX_DAY_EXPENSE days)
 * the lowest expense of some days, click for more(@param MAX_DAY_EXPENSE days)
 * the average value of expense of a day
 *
 * if the user select a month
 * we show
 * expense and sum(compare with last month)
 * the number of tags used in(compare with last month)
 * a pie for all expense on diff tags
 * the highest expense on some tags(and percent), click for more
 * the lowest expense on some tags(and percent), click for more
 * the percent used in food, clothes, house and traffic
 * the highest expense on some tags except the four kinds of tags above
 * a line chart of every day expense
 * the highest expense of some days(and percent), click for more
 * the lowest expense of some days(and percent), click for more
 * the average value of expense of a day
 */

public class ReportViewFragment extends Fragment {

    private final int MAX_DAY_EXPENSE = 10;

    private Context mContext;

    private FloatingActionButton button;
    private ObservableScrollView mScrollView;

    private TextView fromDate;
    private TextView expenseTV;
    private TextView emptyTip;

    private Calendar from = Calendar.getInstance();
    private Calendar to = Calendar.getInstance();

    private SuperToast superToast;

    private PieChartView pie;
    private LineChartView line;

    private MaterialIconView iconRight;
    private MaterialIconView iconLeft;
    private MaterialIconView all;

    private boolean IS_EMPTY = false;

    // store the sum of expenses of each tag
    private Map<Integer, Double> TagExpanse;
    // store the records of each tag
    private Map<Integer, List<CoCoinRecord>> Expanse;
    // the original target value of the whole pie
    private float[] originalTargets;

    // the selected position of one part of the pie
    private int pieSelectedPosition = 0;
    // the last selected position of one part of the pie
    private int lastPieSelectedPosition = -1;

    // the date string on the footer and header
    private String dateString;
    // the date string shown in the dialog
    private String dateShownString;
    // the string shown in the dialog
    private String dialogTitle;

    // the selected tag in pie
    private int tagId = -1;

    // select list
    // year, month(-1 means the whole year), records, expenses
    private ArrayList<double[]> selectListData = null;

    // highest tag list
    private ImageView highestTagIcon;
    private TextView highestTagText;
    private TextView highestTagExpenseTV;
    private TextView highestTagRecord;
    private ExpandedListView highestTags;
    private ReportTagAdapter highestTagsAdapter;
    private ExpandableRelativeLayout highestTagsLayout;

    public static ReportViewFragment newInstance() {
        ReportViewFragment fragment = new ReportViewFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getContext();
        superToast = new SuperToast(mContext);
        superToast.setAnimations(SuperToast.Animations.POPUP);
        superToast.setDuration(SuperToast.Duration.SHORT);
        superToast.setTextColor(Color.parseColor("#ffffff"));
        superToast.setTextSize(SuperToast.TextSize.SMALL);
        superToast.setBackground(SuperToast.Background.RED);
        superToast.getTextView().setTypeface(CoCoinUtil.typefaceLatoLight);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report_view, container, false);
    }

    private MaterialDialog dialog;
    private View dialogView;
    private MyGridView myGridView;
    private DialogMonthSelectGridViewAdapter dialogMonthSelectGridViewAdapter;
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        IS_EMPTY = RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS.isEmpty();

        mScrollView = (ObservableScrollView) view.findViewById(R.id.scrollView);

        MaterialViewPagerHelper.registerScrollView(getActivity(), mScrollView, null);

//        fromDate = (TextView)view.findViewById(R.id.from_date);
//        fromDate.setTypeface(CoCoinUtil.GetTypeface());

        expenseTV = (TextView)view.findViewById(R.id.expense);
        expenseTV.setTypeface(CoCoinUtil.typefaceLatoLight);
        expenseTV.setText(CoCoinUtil.GetInMoney(0));

        pie = (PieChartView)view.findViewById(R.id.chart_pie);
        pie.setVisibility(View.INVISIBLE);

        iconRight = (MaterialIconView)view.findViewById(R.id.icon_right);
        iconLeft = (MaterialIconView)view.findViewById(R.id.icon_left);
        iconRight.setVisibility(View.INVISIBLE);
        iconLeft.setVisibility(View.INVISIBLE);

//        all = (MaterialIconView)view.findViewById(R.id.all);
//        all.setVisibility(View.INVISIBLE);

        emptyTip = (TextView)view.findViewById(R.id.empty_tip);
        emptyTip.setTypeface(CoCoinUtil.GetTypeface());

        highestTagIcon = (ImageView)view.findViewById(R.id.highest_tag_icon);
        highestTagIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (highestTagsLayout.isExpanded()) {
                    highestTagsLayout.collapse();
                } else {
                    highestTagsLayout.expand();
                }
            }
        });
        highestTagText = (TextView)view.findViewById(R.id.highest_tag_text);
        highestTagExpenseTV = (TextView)view.findViewById(R.id.highest_tag_expense);
        highestTagRecord = (TextView)view.findViewById(R.id.highest_tag_sum);
        highestTags = (ExpandedListView)view.findViewById(R.id.highest_tags);
        highestTagsLayout = (ExpandableRelativeLayout) view.findViewById(R.id.expand_highest_tag);

        if (IS_EMPTY) {
            emptyTip.setVisibility(View.GONE);
        }

        button = (FloatingActionButton) view.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectListData == null) new GetSelectListData(true).execute();
                else showSelectListDataDialog();
//                dialog = new MaterialDialog.Builder(mContext)
//                        .customView(R.layout.dialog_select_month, false)
//                        .negativeText(R.string.cancel)
//                        .show();
//                dialogView = dialog.getCustomView();
//                myGridView = (MyGridView)dialogView.findViewById(R.id.grid_view);
//                dialogMonthSelectGridViewAdapter = new DialogMonthSelectGridViewAdapter(mContext);
//                myGridView.setAdapter(dialogMonthSelectGridViewAdapter);
//
//                myGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//                    @Override
//                    public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
//                        dialog.dismiss();
//                        String content = "";
//                        if (((position - 1) % 16 == 0) || ((position - 2) % 16 == 0) || ((position - 3) % 16 == 0)) content = "";
//                        else if (position % 16 == 0) content = (dialogMonthSelectGridViewAdapter.getMinYear() + (position / 16)) + "";
//                        else content = CoCoinUtil.getInstance().GetMonthShort(position % 16 - 3);
//                        if (!"".equals(content)) CoCoinUtil.getInstance().showToast(mContext, content);
//                    }
//                });
            }
        });

        new GetSelectListData(false).execute();

    }

    @Override
    public void onDestroy() {

        super.onDestroy();

        RefWatcher refWatcher = CoCoinApplication.getRefWatcher(getActivity());
        refWatcher.watch(this);
    }

    Pair<Boolean, SublimeOptions> getOptions() {
        SublimeOptions options = new SublimeOptions();
        int displayOptions = 0;

        displayOptions |= SublimeOptions.ACTIVATE_DATE_PICKER;

        options.setPickerToShow(SublimeOptions.Picker.DATE_PICKER);

        options.setDisplayOptions(displayOptions);

        return new Pair<>(displayOptions != 0 ? Boolean.TRUE : Boolean.FALSE, options);
    }

    private class mActionClickListenerForPie implements ActionClickListener {
        @Override
        public void onActionClicked(Snackbar snackbar) {
            List<CoCoinRecord> shownCoCoinRecords = Expanse.get(tagId);
            ((FragmentActivity)mContext).getSupportFragmentManager()
                    .beginTransaction()
                    .add(new RecordCheckDialogFragment(
                            mContext, shownCoCoinRecords, dialogTitle), "MyDialog")
                    .commit();
        }
    }

    // get select list for dialog
    private MaterialDialog progressDialog;
    public class GetSelectListData extends AsyncTask<String, Void, String> {

        private boolean openDialog;

        public GetSelectListData(boolean openDialog) {
            this.openDialog = openDialog;
            progressDialog = new MaterialDialog.Builder(mContext)
                    .title(R.string.report_loading_select_list_title)
                    .content(R.string.report_loading_select_list_content)
                    .cancelable(false)
                    .progress(true, 0)
                    .show();
        }
        @Override
        protected String doInBackground(String... params) {
            selectListData = new ArrayList<>();
            int size = RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS.size();
            int currentYearSelectListPosition = -1;
            int currentMonthSelectListPosition = -1;
            int currentYear = -1;
            int currentMonth = -1;
            for (int i = size - 1; i >= 0; i--) {
                CoCoinRecord record = RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS.get(i);
                if (record.getCalendar().get(Calendar.YEAR) != currentYear) {
                    double[] newYearSelectList = {record.getCalendar().get(Calendar.YEAR), -1, 1, record.getMoney()};
                    selectListData.add(newYearSelectList);
                    currentYearSelectListPosition = selectListData.size() - 1;
                    currentYear = record.getCalendar().get(Calendar.YEAR);
                    // if the year is different, we have to add new year and month
                    double[] newMonthSelectList = {record.getCalendar().get(Calendar.YEAR), record.getCalendar().get(Calendar.MONTH) + 1, 1, record.getMoney()};
                    selectListData.add(newMonthSelectList);
                    currentMonthSelectListPosition = selectListData.size() - 1;
                    currentMonth = record.getCalendar().get(Calendar.MONTH);
                } else {
                    if (record.getCalendar().get(Calendar.MONTH) != currentMonth) {
                        double[] newMonthSelectList = {record.getCalendar().get(Calendar.YEAR), record.getCalendar().get(Calendar.MONTH) + 1, 1, record.getMoney()};
                        selectListData.add(newMonthSelectList);
                        currentMonth = record.getCalendar().get(Calendar.MONTH);
                    } else {
                        selectListData.get(currentYearSelectListPosition)[2]++;
                        selectListData.get(currentYearSelectListPosition)[3] += record.getMoney();
                        selectListData.get(currentMonthSelectListPosition)[2]++;
                        selectListData.get(currentMonthSelectListPosition)[3] += record.getMoney();
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                for (int i = 0; i < selectListData.size(); i++) {
                    Log.d("CoCoin", "Select List Data: " + selectListData.get(i)[0] + " " + selectListData.get(i)[1] + " " + selectListData.get(i)[2] + " " + selectListData.get(i)[3]);
                }
            }
            return null;
        }
        @Override
        protected void onPostExecute(String result) {
            if (progressDialog != null) progressDialog.cancel();
            if (openDialog) showSelectListDataDialog();
        }
    }

    private void showSelectListDataDialog() {
        new MaterialDialog.Builder(mContext)
                .title(R.string.report_select_list_title)
                .cancelable(false)
                .negativeText(R.string.cancel)
                .adapter(new DialogSelectListDataAdapter(selectListData),
                        new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                                dialog.dismiss();
                                makeReport(which);
                            }
                        })
                .show();
    }

    private void makeReport(int p) {
        if (selectListData.get(p)[1] == -1) {
            // select year
            from.set((int)selectListData.get(p)[0], 0, 1, 0, 0, 0);
            from.add(Calendar.SECOND, 0);
            to.set((int)selectListData.get(p)[0], 11, 31, 23, 59, 59);
            to.add(Calendar.SECOND, 0);
            new GetReport(from, to, true).execute();
        } else {
            // select month
            from.set((int)selectListData.get(p)[0], (int)selectListData.get(p)[1], 1, 0, 0, 0);
            from.add(Calendar.SECOND, 0);
            to.set((int)selectListData.get(p)[0], (int)selectListData.get(p)[1], from.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
            to.add(Calendar.SECOND, 0);
            new GetReport(from, to, true).execute();
        }
    }

    int reportYear = -1;
    int reportMonth = -1;
    double expense = 0;
    int records = 0;
    int tags = 0;
    PieChartData pieChartData = null;
    // expense, percent, tagId, records
    ArrayList<double[]> highestTagExpense = null;
    ArrayList<double[]> lowestTagExpense = null;
    // expense and percent on clothes, food, house and traffic
    ArrayList<double[]> needExpense = null;
    // expense, percent, tagId except the above four tags
    ArrayList<double[]> needlessExpense = null;
    LineChartData lineChartData = null;
    // year, month, day of month, expense and percent of diff months
    ArrayList<double[]> highestMonthExpense = null;
    ArrayList<double[]> lowestMonthExpense = null;
    double averageMonthExpense = -1;
    // year, month, day of month, expense and percent of diff days, most @param MAX_DAY_EXPENSE days
    ArrayList<double[]> highestDayExpense = null;
    ArrayList<double[]> lowestDayExpense = null;
    double averageDayExpense = -1;
    public class GetReport extends AsyncTask<String, Void, String> {

        private Calendar from;
        private Calendar to;
        private boolean isYear;

        public GetReport(Calendar from, Calendar to, boolean isYear) {
            this.from = from;
            this.to = to;
            this.isYear = isYear;
            progressDialog = new MaterialDialog.Builder(mContext)
                    .title(R.string.report_loading_select_list_title)
                    .content(R.string.report_loading_select_list_content)
                    .cancelable(false)
                    .progress(true, 0)
                    .show();
        }
        @Override
        protected String doInBackground(String... params) {
            expense = 0;
            records = 0;
            tags = 0;
            highestTagExpense = new ArrayList<>();
            lowestTagExpense = new ArrayList<>();
            needExpense = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                double[] aTag = {0, 0};
                needExpense.add(aTag);
            }
            if (isYear) {
                highestMonthExpense = new ArrayList<>();
                lowestMonthExpense = new ArrayList<>();
            }
            highestDayExpense = new ArrayList<>();
            lowestDayExpense = new ArrayList<>();

            double[] tagExpense = new double[RecordManager.getInstance(CoCoinApplication.getAppContext()).TAGS.size() + 1];
            for (int i = tagExpense.length - 1; i >= 0; i--) tagExpense[i] = 0;
            double[] tagRecords = new double[RecordManager.getInstance(CoCoinApplication.getAppContext()).TAGS.size() + 1];
            for (int i = tagRecords.length - 1; i >= 0; i--) tagRecords[i] = 0;

            // month and expense
            ArrayList<double[]> monthExpense = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                double[] aMonth = {i, 0};
                monthExpense.add(aMonth);
            }

            reportYear = to.get(Calendar.YEAR);
            reportMonth = to.get(Calendar.MONTH) + 1;

            // month, day and expense
            double[][] dayExpense = new double[12][32];
            for (int i = 0; i < 12; i++) {
                for (int j = 1; j <= 31; j++) {
                    dayExpense[i][j] = 0;
                }
            }

            int size = RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS.size();
            for (int i = size - 1; i >= 0; i--) {
                CoCoinRecord record = RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS.get(i);
                if (record.getCalendar().before(from)) break;
                if (!record.getCalendar().after(to)) {
                    for (int j = i; j >= 0; j--) {
                        CoCoinRecord r = RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS.get(i);
                        if (r.getCalendar().before(from)) break;
                        // here is the record we need
                        expense += r.getMoney();
                        records++;
                        tagExpense[r.getTag()] += r.getMoney();
                        tagRecords[r.getTag()]++;
                        if (isYear) {
                            monthExpense.get(r.getCalendar().get(Calendar.MONTH))[1] += r.getMoney();
                            dayExpense[r.getCalendar().get(Calendar.MONTH)][r.getCalendar().get(Calendar.DAY_OF_MONTH)] += r.getMoney();
                        } else {
                            dayExpense[r.getCalendar().get(Calendar.MONTH)][r.getCalendar().get(Calendar.DAY_OF_MONTH)] += r.getMoney();
                        }
                    }
                }
            }

            for (int i = 0; i < tagExpense.length; i++) {
                if (tagExpense[i] != 0) {
                    int cfht = CoCoinUtil.IsCFHT(i);
                    if (cfht != -1) {
                        needExpense.get(cfht)[0] += tagExpense[i];
                    }
                    tags++;
                    double[] aTag = {tagExpense[i], tagExpense[i] / expense, i, tagRecords[i]};
                    highestTagExpense.add(aTag);
                    lowestTagExpense.add(aTag);
                }
            }
            for (int i = 0; i < 4; i++) needExpense.get(i)[1] = needExpense.get(i)[0] / expense;
            Collections.sort(highestTagExpense, new Comparator<double[]>() {
                @Override
                public int compare(double[] lhs, double[] rhs) {
                    return Double.compare(lhs[0], rhs[1]);
                }
            });
            Collections.sort(lowestTagExpense, new Comparator<double[]>() {
                @Override
                public int compare(double[] lhs, double[] rhs) {
                    return Double.compare(rhs[0], lhs[1]);
                }
            });
            // use tag expense values to generate pie data
            ArrayList<SliceValue> sliceValues = new ArrayList<>();
            for (int i = 0; i < highestTagExpense.size(); i++) {
                SliceValue sliceValue = new SliceValue(
                        (float)(double)highestTagExpense.get(i)[0], CoCoinUtil.GetTagColor((int)highestTagExpense.get(i)[2]));
                sliceValue.setLabel(String.valueOf((int)highestTagExpense.get(i)[2]));
                sliceValues.add(sliceValue);
            }
            pieChartData = new PieChartData(sliceValues);
            pieChartData.setHasLabels(false);
            pieChartData.setHasLabelsOnlyForSelected(false);
            pieChartData.setHasLabelsOutside(false);
            pieChartData.setHasCenterCircle(SettingManager.getInstance().getIsHollow());

            if (isYear) {
                Collections.sort(monthExpense, new Comparator<double[]>() {
                    @Override
                    public int compare(double[] lhs, double[] rhs) {
                        return Double.compare(rhs[1], lhs[1]);
                    }
                });
                for (int i = 0; i < 12; i++) {
                    double[] aMonth = {reportYear, monthExpense.get(i)[0], -1, monthExpense.get(i)[1], monthExpense.get(i)[1] / expense};
                    highestMonthExpense.add(aMonth);
                }
                for (int i = 11; i >= 0; i--) {
                    double[] aMonth = {reportYear, monthExpense.get(i)[0], -1, monthExpense.get(i)[1], monthExpense.get(i)[1] / expense};
                    lowestMonthExpense.add(aMonth);
                }

                averageMonthExpense = expense / 12;

                ArrayList<double[]> dayExpense2 = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(reportYear, i, 1, 0, 0, 0);
                    calendar.add(Calendar.SECOND, 0);
                    int dayOfMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
                    for (int j = 0; j < dayOfMonth; j++) {
                        double[] aDay = {i, j, dayExpense[i][j]};
                        dayExpense2.add(aDay);
                    }
                }
                Collections.sort(dayExpense2, new Comparator<double[]>() {
                    @Override
                    public int compare(double[] lhs, double[] rhs) {
                        return Double.compare(rhs[2], lhs[2]);
                    }
                });
                for (int i = 0; i < MAX_DAY_EXPENSE; i++) {
                    if (i >= dayExpense2.size() || dayExpense2.get(i)[2] == 0) break;
                    double[] aDay = {reportYear, dayExpense2.get(i)[0], dayExpense2.get(i)[1], dayExpense2.get(i)[2], dayExpense2.get(i)[2] / expense};
                    highestDayExpense.add(aDay);
                }
                for (int i = dayExpense2.size() - 1; i >= dayExpense2.size() - MAX_DAY_EXPENSE; i--) {
                    if (i < 0 || dayExpense2.get(i)[2] == 0) break;
                    double[] aDay = {reportYear, dayExpense2.get(i)[0], dayExpense2.get(i)[1], dayExpense2.get(i)[2], dayExpense2.get(i)[2] / expense};
                    lowestDayExpense.add(aDay);
                }

                Calendar calendar = Calendar.getInstance();
                calendar.set(reportYear, 0, 1, 0, 0, 0);
                calendar.add(Calendar.SECOND, 0);
                averageDayExpense = expense / calendar.getActualMaximum(Calendar.DAY_OF_YEAR);
            } else {
                ArrayList<double[]> dayExpense2 = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(reportYear, i, 1, 0, 0, 0);
                    calendar.add(Calendar.SECOND, 0);
                    int dayOfMonth = calendar.getActualMaximum(Calendar.MONTH);
                    for (int j = 0; j < dayOfMonth; j++) {
                        double[] aDay = {i, j, dayExpense[i][j]};
                        dayExpense2.add(aDay);
                    }
                }
                Collections.sort(dayExpense2, new Comparator<double[]>() {
                    @Override
                    public int compare(double[] lhs, double[] rhs) {
                        return Double.compare(rhs[2], lhs[2]);
                    }
                });
                for (int i = 0; i < MAX_DAY_EXPENSE; i++) {
                    if (i >= dayExpense2.size() || dayExpense2.get(i)[2] == 0) break;
                    double[] aDay = {reportYear, dayExpense2.get(i)[0], dayExpense2.get(i)[1], dayExpense2.get(i)[2], dayExpense2.get(i)[2] / expense};
                    highestDayExpense.add(aDay);
                }
                for (int i = dayExpense2.size() - 1; i >= dayExpense2.size() - MAX_DAY_EXPENSE; i--) {
                    if (i < 0 || dayExpense2.get(i)[2] == 0) break;
                    double[] aDay = {reportYear, dayExpense2.get(i)[0], dayExpense2.get(i)[1], dayExpense2.get(i)[2], dayExpense2.get(i)[2] / expense};
                    lowestDayExpense.add(aDay);
                }

                Calendar calendar = Calendar.getInstance();
                calendar.set(reportYear, reportMonth - 1, 1, 0, 0, 0);
                calendar.add(Calendar.SECOND, 0);
                averageDayExpense = expense / calendar.getActualMaximum(Calendar.DAY_OF_YEAR);
            }

            // use month/day expense values to generate line data
            if (isYear) {
                List<Line> lines = new ArrayList<>();
                for (int i = 0; i < 1; ++i) {

                    List<PointValue> values = new ArrayList<>();
                    for (int j = 0; j < 12; ++j) {
                        values.add(new PointValue(j, (float)monthExpense.get(j)[1]));
                    }

                    Line line = new Line(values);
                    line.setColor(ChartUtils.COLORS[i]);
                    line.setShape(ValueShape.CIRCLE);
                    line.setCubic(false);
                    line.setFilled(false);
                    line.setHasLabels(false);
                    line.setHasLabelsOnlyForSelected(false);
                    line.setHasLines(true);
                    line.setHasPoints(true);
                    lines.add(line);
                }

                lineChartData = new LineChartData(lines);
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.set(reportYear, reportMonth - 1, 1, 0, 0, 0);
                calendar.add(Calendar.SECOND, 0);
                int days = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
                List<Line> lines = new ArrayList<>();
                for (int i = 0; i < 1; ++i) {

                    List<PointValue> values = new ArrayList<>();
                    for (int j = 1; j <= days; ++j) {
                        values.add(new PointValue(j, (float)dayExpense[reportMonth - 1][j]));
                    }

                    Line line = new Line(values);
                    line.setColor(ChartUtils.COLORS[i]);
                    line.setShape(ValueShape.CIRCLE);
                    line.setCubic(false);
                    line.setFilled(false);
                    line.setHasLabels(false);
                    line.setHasLabelsOnlyForSelected(false);
                    line.setHasLines(true);
                    line.setHasPoints(true);
                    lines.add(line);
                }

                lineChartData = new LineChartData(lines);
            }

            return null;
        }
        @Override
        protected void onPostExecute(String result) {
            if (progressDialog != null) progressDialog.cancel();
            highestTagsAdapter = new ReportTagAdapter(highestTagExpense);
            highestTags.setAdapter(highestTagsAdapter);
        }
    }

//    private void select() {
//
//        if (RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS == null
//                || RecordManager.getInstance(CoCoinApplication.getAppContext()).RECORDS.size() == 0) {
//            return;
//        }
//
//        start = -1;
//        end = 0;
//        Sum = 0;
//        lastPieSelectedPosition = -1;
//
//        if (from.after(RecordManager.RECORDS.get(RecordManager.RECORDS.size() - 1).getCalendar())) {
//            return;
//        }
//        if (to.before(RecordManager.RECORDS.get(0).getCalendar())) {
//            return;
//        }
//
//        for (int i = RecordManager.RECORDS.size() - 1; i >= 0; i--) {
//            if (RecordManager.RECORDS.get(i).getCalendar().before(from)) {
//                end = i + 1;
//                break;
//            } else if (RecordManager.RECORDS.get(i).getCalendar().before(to)) {
//                if (start == -1) {
//                    start = i;
//                }
//            }
//        }
//
//        startDayCalendar = (Calendar)from.clone();
//        startDayCalendar.set(Calendar.HOUR_OF_DAY, 0);
//        startDayCalendar.set(Calendar.MINUTE, 0);
//        startDayCalendar.set(Calendar.SECOND, 0);
//        final long startDay = TimeUnit.MILLISECONDS.toDays(startDayCalendar.getTimeInMillis());
//        final long days = TimeUnit.MILLISECONDS.toDays(to.getTimeInMillis()) - startDay + 1;
//
//        TagExpanse = new TreeMap<>();
//        Expanse = new HashMap<>();
//        originalTargets = new float[(int)days];
//
//        int size = RecordManager.TAGS.size();
//        for (int j = 2; j < size; j++) {
//            TagExpanse.put(RecordManager.TAGS.get(j).getId(), Double.valueOf(0));
//            Expanse.put(RecordManager.TAGS.get(j).getId(), new ArrayList<CoCoinRecord>());
//        }
//
//        for (int i = start; i >= end; i--) {
//            CoCoinRecord coCoinRecord = RecordManager.RECORDS.get(i);
//            TagExpanse.put(coCoinRecord.getTag(),
//                    TagExpanse.get(coCoinRecord.getTag()) + Double.valueOf(coCoinRecord.getMoney()));
//            Expanse.get(coCoinRecord.getTag()).add(coCoinRecord);
//            Sum += coCoinRecord.getMoney();
//            originalTargets[(int)(TimeUnit.MILLISECONDS.toDays(
//                    coCoinRecord.getCalendar().getTimeInMillis()) - startDay)] += coCoinRecord.getMoney();
//        }
//
//        expense.setText(CoCoinUtil.GetInMoney(Sum));
//        emptyTip.setVisibility(View.GONE);
//
//        TagExpanse = CoCoinUtil.SortTreeMapByValues(TagExpanse);
//
//        final ArrayList<SliceValue> sliceValues = new ArrayList<>();
//
//        for (Map.Entry<Integer, Double> entry : TagExpanse.entrySet()) {
//            if (entry.getValue() >= 1) {
//                SliceValue sliceValue = new SliceValue(
//                        (float)(double)entry.getValue(),
//                        CoCoinUtil.GetTagColor(entry.getKey()));
//                sliceValue.setLabel(String.valueOf(entry.getKey()));
//                sliceValues.add(sliceValue);
//            }
//        }
//
//        final PieChartData pieChartData = new PieChartData(sliceValues);
//
//        pieChartData.setHasLabels(false);
//        pieChartData.setHasLabelsOnlyForSelected(false);
//        pieChartData.setHasLabelsOutside(false);
//        pieChartData.setHasCenterCircle(SettingManager.getInstance().getIsHollow());
//
//        pie.setPieChartData(pieChartData);
//        pie.setChartRotationEnabled(false);
//
//        pie.setVisibility(View.VISIBLE);
//
//        iconRight.setVisibility(View.VISIBLE);
//        iconRight.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (lastPieSelectedPosition != -1) {
//                    pieSelectedPosition = lastPieSelectedPosition;
//                }
//                pieSelectedPosition
//                        = (pieSelectedPosition - 1 + sliceValues.size())
//                        % sliceValues.size();
//                SelectedValue selectedValue =
//                        new SelectedValue(
//                                pieSelectedPosition,
//                                0,
//                                SelectedValue.SelectedValueType.NONE);
//                pie.selectValue(selectedValue);
//            }
//        });
//        iconLeft.setVisibility(View.VISIBLE);
//        iconLeft.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (lastPieSelectedPosition != -1) {
//                    pieSelectedPosition = lastPieSelectedPosition;
//                }
//                pieSelectedPosition
//                        = (pieSelectedPosition + 1)
//                        % sliceValues.size();
//                SelectedValue selectedValue =
//                        new SelectedValue(
//                                pieSelectedPosition,
//                                0,
//                                SelectedValue.SelectedValueType.NONE);
//                pie.selectValue(selectedValue);
//            }
//        });
//
//// set value touch listener of pie//////////////////////////////////////////////////////////////////
//
//        dateShownString = mContext.getResources().getString(R.string.from) + " " +
//                CoCoinUtil.GetMonthShort(from.get(Calendar.MONTH) + 1) + " " +
//                from.get(Calendar.DAY_OF_MONTH) + " " +
//                from.get(Calendar.YEAR) + " " +
//                mContext.getResources().getString(R.string.to) + " " +
//                CoCoinUtil.GetMonthShort(to.get(Calendar.MONTH) + 1) + " " +
//                to.get(Calendar.DAY_OF_MONTH) + " " +
//                to.get(Calendar.YEAR);
//
//        pie.setOnValueTouchListener(new PieChartOnValueSelectListener() {
//            @Override
//            public void onValueSelected(int p, SliceValue sliceValue) {
//                // snack bar
//                String text;
//                tagId = Integer.valueOf(String.valueOf(sliceValue.getLabelAsChars()));
//                double percent = sliceValue.getValue() / Sum * 100;
//                if ("zh".equals(CoCoinUtil.GetLanguage())) {
//                    text = CoCoinUtil.GetSpendString((int) sliceValue.getValue()) +
//                            CoCoinUtil.GetPercentString(percent) + "\n" +
//                            "于" + CoCoinUtil.GetTagName(tagId);
//                } else {
//                    text = CoCoinUtil.GetSpendString((int) sliceValue.getValue())
//                            + " (takes " + String.format("%.2f", percent) + "%)\n"
//                            + "in " + CoCoinUtil.GetTagName(tagId);
//                }
//                if ("zh".equals(CoCoinUtil.GetLanguage())) {
//                    dialogTitle = dateShownString + "\n" +
//                            CoCoinUtil.GetSpendString((int) sliceValue.getValue()) + " " +
//                            "于" + CoCoinUtil.GetTagName(tagId);
//                } else {
//                    dialogTitle = CoCoinUtil.GetSpendString((int) sliceValue.getValue()) + " " +
//                            mContext.getResources().getString(R.string.from) + " " +
//                            CoCoinUtil.GetMonthShort(from.get(Calendar.MONTH) + 1) + " " +
//                            from.get(Calendar.DAY_OF_MONTH) + " " +
//                            from.get(Calendar.YEAR) + "\n" +
//                            mContext.getResources().getString(R.string.to) + " " +
//                            CoCoinUtil.GetMonthShort(to.get(Calendar.MONTH) + 1) + " " +
//                            to.get(Calendar.DAY_OF_MONTH) + " " +
//                            to.get(Calendar.YEAR) + " " +
//                            "in " + CoCoinUtil.GetTagName(tagId);
//                }
//                Snackbar snackbar =
//                        Snackbar
//                                .with(mContext)
//                                .type(SnackbarType.MULTI_LINE)
//                                .duration(Snackbar.SnackbarDuration.LENGTH_SHORT)
//                                .position(Snackbar.SnackbarPosition.BOTTOM)
//                                .margin(15, 15)
//                                .backgroundDrawable(CoCoinUtil.GetSnackBarBackground(-3))
//                                .text(text)
//                                .textTypeface(CoCoinUtil.GetTypeface())
//                                .textColor(Color.WHITE)
//                                .actionLabelTypeface(CoCoinUtil.GetTypeface())
//                                .actionLabel(mContext.getResources()
//                                        .getString(R.string.check))
//                                .actionColor(Color.WHITE)
//                                .actionListener(new mActionClickListenerForPie());
//                SnackbarManager.show(snackbar);
//
//                if (p == lastPieSelectedPosition) {
//                    return;
//                } else {
//                    lastPieSelectedPosition = p;
//                }
//            }
//
//            @Override
//            public void onValueDeselected() {
//
//            }
//        });
//
//        all.setVisibility(View.VISIBLE);
//        all.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                List<CoCoinRecord> data = new LinkedList<CoCoinRecord>();
//                for (int i = start; i >= end; i--) data.add(RecordManager.RECORDS.get(i));
//                if ("zh".equals(CoCoinUtil.GetLanguage())) {
//                    dialogTitle = dateShownString + "\n" +
//                            CoCoinUtil.GetSpendString(Sum) +
//                            "于" + CoCoinUtil.GetTagName(tagId);
//                } else {
//                    dialogTitle = CoCoinUtil.GetSpendString(Sum) + " "
//                            + mContext.getResources().getString(R.string.from) + " " +
//                            CoCoinUtil.GetMonthShort(from.get(Calendar.MONTH) + 1) + " " +
//                            from.get(Calendar.DAY_OF_MONTH) + " " +
//                            from.get(Calendar.YEAR) + "\n" +
//                            mContext.getResources().getString(R.string.to) + " " +
//                            CoCoinUtil.GetMonthShort(to.get(Calendar.MONTH) + 1) + " " +
//                            to.get(Calendar.DAY_OF_MONTH) + " " +
//                            to.get(Calendar.YEAR) + " " +
//                            "in " + CoCoinUtil.GetTagName(tagId);
//                }
//                ((FragmentActivity)mContext).getSupportFragmentManager()
//                        .beginTransaction()
//                        .add(new RecordCheckDialogFragment(
//                                mContext, data, dialogTitle), "MyDialog")
//                        .commit();
//            }
//        });
//    }

}