package com.example.handwriting.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.example.handwriting.util.WritingUtil;

import java.util.ArrayList;
import java.util.List;

public class HandwritingView extends View {
    private Paint mOutlinePaint, mFillPaint;
    private List<List<Point>> mFrameData = new ArrayList<>();
    private List<List<Point>> mFillData = new ArrayList<>();
    private Path path = new Path();
    //控件宽高
    private int width = 0, height = 0;
    //当前绘制笔画数
    private int mStrokeStep = 1;
    //单笔最大填充数
    private int mFillMaxStep;
    //动画延迟时间
    private int mDrawDelay = 50;

    public HandwritingView(Context context) {
        super(context);
        init();
    }

    public HandwritingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HandwritingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mOutlinePaint = new Paint();
        mOutlinePaint.setColor(Color.BLACK);
        mOutlinePaint.setStrokeJoin(Paint.Join.ROUND);
        mOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        mOutlinePaint.setAntiAlias(true);
        //Paint.Style.FILL_AND_STROKE 为实心
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeWidth(10);

        mFillPaint = new Paint();
        mFillPaint.setColor(Color.RED);
        mFillPaint.setStrokeJoin(Paint.Join.ROUND);
        mFillPaint.setStrokeCap(Paint.Cap.ROUND);
        mFillPaint.setAntiAlias(true);
        mFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mFillPaint.setStrokeWidth(10);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = getMeasuredWidth();
        height = getMeasuredHeight();
    }

    public void setText(String text) throws Exception {
        mStrokeStep = 1;
        mFillMaxStep = 10;
        //JSON 数据解析
        String assetsName = WritingUtil.urlEncode(text);
        String jsonData = WritingUtil.getAssestFileContent(getContext(), assetsName);
        mFrameData = WritingUtil.parseJsonFrame(jsonData);
        mFillData = WritingUtil.parseJsonFill(jsonData);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //画边框背景
        drawOutline(canvas);
        //填充
        fillStroke(canvas);
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            invalidate();
        }
    };

    /**
     * 画轮廓
     */
    private void drawOutline(Canvas canvas) {
        try {
            for (int i = 0; i < mFrameData.size(); i++) {
                List<Point> points = mFrameData.get(i);
                canvas.translate(0, 0);
                for (int j = 0; j < points.size(); j++) {
                    Point point = points.get(j);
                    point = scalePoint(point);
                    if (j == 0) {
                        path.moveTo(point.x, point.y);
                    } else {
                        path.lineTo(point.x, point.y);
                    }
                }
                path.close();
                canvas.drawPath(path, mOutlinePaint);
                path.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 填充
     */
    private void fillStroke(Canvas canvas) {
        for (int i = 0; i < mStrokeStep && i < mFillData.size(); i++) {
            List<Point> points = mFillData.get(i);
            if (i == mStrokeStep - 1) {
                //最后一笔每次步进10
                for (int j = 0; j <= mFillMaxStep && j < points.size() - 1; j++) {
                    Point p = points.get(j);
                    p = scalePoint(p);
                    Point nextP = points.get(j + 1);
                    nextP = scalePoint(nextP);
                    canvas.drawLine(p.x, p.y, nextP.x, nextP.y, mFillPaint);
                }
                if (mFillMaxStep == points.size()) {
                    //单笔顺画完
                    mStrokeStep++;
                    mFillMaxStep = 10;
                } else if (mFillMaxStep > points.size()) {
                    //单笔顺画完
                    for (int j = mFillMaxStep - 10; j < points.size() - 1; j++) {
                        Point point = points.get(j);
                        point = scalePoint(point);
                        Point nextPoint = points.get(j + 1);
                        nextPoint = scalePoint(nextPoint);
                        canvas.drawLine(point.x, point.y, nextPoint.x, nextPoint.y, mFillPaint);
                    }
                    mStrokeStep++;
                    mFillMaxStep = 10;
                } else {
                    //步进10
                    mFillMaxStep += 10;
                }
                //最后一笔延迟,产生动画效果
                handler.removeCallbacksAndMessages(null);
                handler.sendEmptyMessageDelayed(1, mDrawDelay);
            } else if (i <= mStrokeStep) {
                //非最后一笔,直接全部填充,无需延迟动画
                for (int j = 0; j < points.size() - 1; j++) {
                    Point p = points.get(j);
                    p = scalePoint(p);
                    Point nextP = points.get(j + 1);
                    nextP = scalePoint(nextP);
                    canvas.drawLine(p.x, p.y, nextP.x, nextP.y, mFillPaint);
                }
            }
        }
    }

    /**
     * 缩放原数据坐标到布局大小
     */
    private Point scalePoint(Point point) {
        Point p = new Point();
        //760 是原数据默认宽高
        p.x = (int) (point.x / (760f / width));
        p.y = (int) (point.y / (760f / height));
        return p;
    }
}
