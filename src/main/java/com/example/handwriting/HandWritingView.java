package com.example.handwriting;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RegionIterator;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HandWritingView extends View {
    private Paint mOutlinePaint;
    private Paint mFillPaint;
    private Paint mTouchPaint;
    private Paint mGridPaint;
    private Paint mDashPaint;
    private Paint mCenterLinePaint;
    //轮廓点数据
    private List<List<Point>> mFrameData = new ArrayList<>();
    //填充点数据
    private List<List<Point>> mFillData = new ArrayList<>();
    //控件宽高
    private int width = 0, height = 0;
    //当前绘制笔画数
    private int mStrokeStep = 1;
    //单笔最大填充数
    private int mFillMaxStep;
    //动画延迟时间
    private int mDrawDelay = 50;

    //触摸画布背景
    private Bitmap touchBitmap;
    //触摸画布
    private Canvas touchCanvas;

    //当前进度
    private int stepIdx = 0;
    //步进长度
    private int singleStepLength = 20;
    //当前绘制画线
    private int currentDrawStroke = -1;

    //不规则范围
    private List<Region> regionList = new ArrayList<>();
    //已经画完的笔画
    private List<Integer> drawFinishStorke = new ArrayList<>();
    //中间线
    private Path centerLinePath = new Path();
    //中间线集合
    private List<Path> centerPaths = new ArrayList<>();
    //触摸轨迹
    private Path touchPath = new Path();
    //触摸点
    private float touchX, touchY;
    //边框轮廓
    private List<Path> mFramePaths = new ArrayList<>();
    //当前触摸笔画
    int currentTouchStroke = -2;

    public HandWritingView(Context context) {
        super(context);
        init();
    }

    public HandWritingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HandWritingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mOutlinePaint = new Paint();
        mOutlinePaint.setColor(Color.DKGRAY);
        mOutlinePaint.setStrokeJoin(Paint.Join.ROUND);
        mOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        mOutlinePaint.setAntiAlias(true);
        //Paint.Style.FILL_AND_STROKE 为实心
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeWidth(2);

        mFillPaint = new Paint();
        mFillPaint.setColor(Color.RED);
        mFillPaint.setStrokeJoin(Paint.Join.ROUND);
        mFillPaint.setStrokeCap(Paint.Cap.ROUND);
        mFillPaint.setAntiAlias(true);
        mFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mFillPaint.setStrokeWidth(5);

        mTouchPaint = new Paint();
        mTouchPaint.setColor(Color.BLUE);
        mTouchPaint.setAntiAlias(true);
        mTouchPaint.setStyle(Paint.Style.FILL);
        //mTouchPaint.setStrokeWidth(2);

        mGridPaint = new Paint();
        mGridPaint.setColor(Color.GRAY);
        mGridPaint.setAntiAlias(true);
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(4);

        mDashPaint = new Paint();
        mDashPaint.setColor(Color.GRAY);
        mDashPaint.setAntiAlias(true);
        mDashPaint.setStyle(Paint.Style.STROKE);
        mDashPaint.setStrokeWidth(4);
        mDashPaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));

        mCenterLinePaint = new Paint();
        mCenterLinePaint.setColor(Color.BLUE);
        mCenterLinePaint.setAntiAlias(true);
        mCenterLinePaint.setStyle(Paint.Style.STROKE);
        mCenterLinePaint.setStrokeWidth(1);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        width = getMeasuredWidth();
        height = getMeasuredHeight();

        Util.width = width;
        Util.height = height;
        if (touchBitmap == null) {
            touchBitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            touchCanvas = new Canvas(touchBitmap);
        }
    }

    /**
     * 设置显示文字
     *
     * @param text 显示文字,部分文字没有数据
     * @throws Exception 无数据
     */
    public void setText(char text) throws Exception {
        centerLinePath.reset();
        centerPaths.clear();
        drawFinishStorke.clear();
        currentDrawStroke = -1;
        mStrokeStep = 1;
        mFillMaxStep = 10;
        mFramePaths.clear();
        regionList.clear();
        //JSON 数据解析
        String assetsName = Util.urlEncode(text);
        String jsonData = Util.readJsonFromAssets(getContext(), assetsName);
        if (TextUtils.isEmpty(jsonData)) {
            setVisibility(View.GONE);
            throw new NullPointerException();
        }
        mFrameData = Util.extractFrame(jsonData);
        mFillData = Util.extractFill(jsonData);
        if (touchCanvas != null) {
            touchCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //画田字格
        drawGrid(canvas);
        //画边框背景
        drawOutline(canvas);
        if (regionList.size() == 0) {
            initData();
        }
        if (centerLinePath.isEmpty() && centerPaths.size() != 0) {
            centerLinePath = centerPaths.get(0);
        }
        //1.打开这行注释显示描红动画
        //fillStroke(canvas);

        //2.触摸描红
        canvas.drawPath(centerLinePath, mCenterLinePaint);
        canvas.drawBitmap(touchBitmap, 0, 0, mTouchPaint);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        System.out.println("hw touch " + event.getAction() + ",x." + event.getX() + ",y." + event.getY());
        float x = event.getX(), y = event.getY();
        touchX = event.getX();
        touchY = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchPath.reset();
                touchPath.moveTo(x, y);
                currentTouchStroke = -1;
                break;
            case MotionEvent.ACTION_MOVE:
                touchPath.lineTo(x, y);
                if (currentTouchStroke < 0) {
                    currentTouchStroke = isInsideStroke(touchPath);
                }
                System.out.println("当前触摸笔画 ---- >" + currentTouchStroke);
                //防止越过正确笔顺
                if (currentDrawStroke + 1 == currentTouchStroke) {
                    //下一笔
                    currentDrawStroke = currentTouchStroke;
                    drawStep();
                } else if (currentDrawStroke == currentTouchStroke) {
                    //当前正在绘制的笔画
                    drawStep();
                } else if (currentTouchStroke != -2) {
                    //笔画进度出错,设置为可用笔画
                    if (currentDrawStroke > currentTouchStroke) {
                        currentDrawStroke = currentTouchStroke;
                    }
                    if (currentDrawStroke < currentTouchStroke - 1) {
                        currentDrawStroke = currentTouchStroke;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        return true;
    }

    //画田字格
    private void drawGrid(Canvas canvas) {
        //边框
        canvas.drawRect(new Rect(2, 2, width - 2, height - 2), mGridPaint);
        //实线
        canvas.drawLine(0, height / 2, width, height / 2, mGridPaint);
        canvas.drawLine(width / 2, 0, width / 2, height, mGridPaint);
        //虚线
        canvas.drawLine(0, 0, width, height, mDashPaint);
        canvas.drawLine(0, height, width, 0, mDashPaint);
    }

    /**
     * 画描红路径,根据手指点
     */
    private void drawStep() {
        System.out.println("=======================");
        System.out.println("drawStep()");
        System.out.println("currentDrawStroke = " + currentDrawStroke);
        System.out.println("stepIdx = " + stepIdx);
        System.out.println("singleStepLength = " + singleStepLength);
        System.out.println("touchX = " + touchX);
        System.out.println("touchY = " + touchY);

        if (currentDrawStroke < 0) {
            return;
        }

        if (currentDrawStroke >= mFillData.size()) {
            return;
        }

        if (!drawFinishStorke.contains(-1)) {
            drawFinishStorke.add(-1);
        }

        if (!drawFinishStorke.contains(currentDrawStroke - 1)) {
            //上一笔没有画完
            return;
        }

        int index = stepIdx + singleStepLength;
        //单笔画总点数
        int strokeTotalPoint = mFillData.get(currentDrawStroke).size();
        //最后末尾一点自动填充
        if (index < strokeTotalPoint * 0.8) {
            //防止绘制进度超过手指范围
            List<Point> points = Util.subList(mFillData.get(currentDrawStroke), (int) (stepIdx - singleStepLength * 1.5), (int) (stepIdx + singleStepLength * 1.5));
            Path drawPath = Util.generatePathByPoint(points);
            Region region = Util.pathToRegion(drawPath);

            if (index > 50 && !region.getBounds().contains((int) touchX, (int) touchY)) {
                //index 小不用判断,用户手指一般不会从0点开始滑动,容错
                return;
            }

            //笔画起点到手指当前距离
            points = Util.subList(mFillData.get(currentDrawStroke), 0, index);

            stepIdx += singleStepLength;

            //画触摸过的区域
            for (int j = 0; j < points.size() - 1; j++) {
                Point p = points.get(j);
                p = scalePoint(p);
                Point nextP = points.get(j + 1);
                nextP = scalePoint(nextP);
                touchCanvas.drawLine(p.x, p.y, nextP.x, nextP.y, mFillPaint);
            }
        } else {
            //单笔画完
            if (!drawFinishStorke.contains(currentDrawStroke)) {
                drawFinishStorke.add(currentDrawStroke);
            }
            //末尾自动填充
            List<Point> points = mFillData.get(currentDrawStroke);
            for (int j = 0; j < points.size() - 1; j++) {
                Point p = points.get(j);
                p = scalePoint(p);
                Point nextP = points.get(j + 1);
                nextP = scalePoint(nextP);
                touchCanvas.drawLine(p.x, p.y, nextP.x, nextP.y, mFillPaint);
            }
            //重置进度
            stepIdx = 0;
            if (currentDrawStroke + 1 < centerPaths.size()) {
                centerLinePath = centerPaths.get(currentDrawStroke + 1);
            }
        }
        invalidate();
    }

    /**
     * 返回指定笔画的中线
     *
     * @param stroke 笔画
     * @return 路径
     */
    private Path getStrokeCenterLine(int stroke) {
        Path path = new Path();
        if (mFillData.size() != 0) {
            List<Point> points = mFillData.get(stroke);
            Point lastPoint = new Point();
            for (int j = 0; j < points.size() - 1; j++) {
                Point p = points.get(j);
                p = scalePoint(p);
                Point nextP = points.get(j + 1);
                nextP = scalePoint(nextP);

                Rect rect = new Rect(p.x, p.y, nextP.x, nextP.y);
                if (j == 0) {
                    path.moveTo(rect.centerX(), rect.centerY());
                    lastPoint.set(rect.centerX(), rect.centerY());
                } else {
                    if (j % 12 == 0) {
                        path.quadTo(lastPoint.x, lastPoint.y, rect.centerX(), rect.centerY());
                        lastPoint.set(rect.centerX(), rect.centerY());
                    }
                }
            }
        }
        return path;
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
                Path path = new Path();
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
                //添加缓存
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void initData() {
        //init path frame
        mFramePaths.clear();
        for (int i = 0; i < mFrameData.size(); i++) {
            Path path = new Path();
            List<Point> points = mFrameData.get(i);
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
            mFramePaths.add(path);
            //添加缓存
        }
        //regionList
        RectF bounds = new RectF();
        for (int i = 0; i < mFramePaths.size(); i++) {
            bounds.setEmpty();
            mFramePaths.get(i).computeBounds(bounds, true);
            Region region = new Region();
            region.setPath(mFramePaths.get(i), new Region((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom));
            regionList.add(region);
        }
        for (int i = 0; i < mFrameData.size(); i++) {
            centerPaths.add(i, getStrokeCenterLine(i));
        }
    }

    private boolean pointInPath(Path path, Point point) {
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        Region region = new Region();
        region.setPath(path, new Region((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom));
        return region.contains(point.x, point.y);
    }

    /**
     * 判断某一个坐标在哪一个笔画中
     *
     * @param point 坐标点
     * @return 笔画
     */
    private int isInsideStroke(Point point) {
        //笔画重叠会找到多个
        List<Integer> strokes = new ArrayList<>();
        for (int i = 0; i < mFramePaths.size(); i++) {
            Region region = regionList.get(i);
            //判断关键点是否在某一笔画中
            if (region.contains(point.x, point.y)) {
                strokes.add(i);
            }
        }

        if (strokes.contains(currentDrawStroke)) {
            return currentDrawStroke;
        } else if (strokes.size() != 0) {
            return strokes.get(0);
        }
        return -2;
    }

    /**
     * 判断当前path 在哪一个笔画中
     *
     * @param p 路径
     * @return 笔画数
     */
    private int isInsideStroke(Path p) {
        //取出所有笔画
        PathMeasure measure = new PathMeasure(p, true);
        float len = measure.getLength();
        if (len < 50) {
            //触摸太短
            //return -2;
        }
        for (int i = 0; i < mFramePaths.size(); i++) {
            Region region = regionList.get(i);
            //判断关键点是否在某一笔画中
            List<Point> points = getPointInPath(p, 40);
            int containCount = 0;
            for (int j = 0; j < points.size(); j++) {
                Point point = points.get(j);
                if (!region.contains(point.x, point.y)) {
                    if (containCount == 0) {
                        continue;
                    } else {
                        break;
                    }
                } else {
                    containCount++;
                }
                if (containCount > 20) {
                    //相似度很高
                    return i;
                }
                if (j == points.size() - 1) {
                    return i;
                }
            }
        }

        return -3;
    }

    private void drawRegion(Canvas canvas, Region rgn, Paint paint) {
        RegionIterator iter = new RegionIterator(rgn);
        Rect r = new Rect();
        while (iter.next(r)) {
            int c = Color.rgb(random(), random(), random());
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(c);
            canvas.drawRect(r, paint);
        }
    }


    private int random() {
        Random random = new Random();
        return random.nextInt(200);
    }


    /**
     * 画某一笔
     *
     * @param canvas 画布
     * @param storke 笔数
     */
    private void drawStorke(Canvas canvas, int storke) {
        for (int i = 0; i <= storke && i < mFillData.size(); i++) {
            List<Point> points = mFillData.get(i);
            for (int j = 0; j < points.size() - 1; j++) {
                Point p = points.get(j);
                p = scalePoint(p);
                Point nextP = points.get(j + 1);
                nextP = scalePoint(nextP);
                canvas.drawLine(p.x, p.y, nextP.x, nextP.y, mFillPaint);
            }
        }
    }

    /**
     * 获取path中的所有点
     *
     * @param p    路径
     * @param step 间隔
     * @return
     */
    private List<Point> getPointInPath(Path p, int step) {
        List<Point> list = new ArrayList<>();
        PathMeasure measure = new PathMeasure(p, false);
        float length = measure.getLength();
        float distance = 0f;
        float speed = length / step;
        int counter = 0;
        float[] aCoordinates = new float[2];
        while ((distance < length) && (counter < step)) {
            // get point from the path
            measure.getPosTan(distance, aCoordinates, null);
            Point point = new Point((int) aCoordinates[0], (int) aCoordinates[1]);
            list.add(point);
            counter++;
            distance = distance + speed;
        }
        return list;
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

    private static class Util {
        private static int width, height;

        /**
         * Url 编码
         *
         * @param c 字符
         * @return 编码结果
         * @throws Exception 编码异常
         */
        static String urlEncode(char c) throws Exception {
            String encode = URLEncoder.encode(String.valueOf(c), "utf-8");
            encode = encode.replaceAll("%", "");
            return encode.toLowerCase();
        }

        /**
         * 获取本地文字数据
         *
         * @param name 文件名
         * @return 数据
         */
        static String readJsonFromFile(String name) {
            File dataFile = new File(Environment.getExternalStorageDirectory().getPath() + "/qimon/dictionary/data/", name);
            StringBuilder fileContent = new StringBuilder("");
            if (!dataFile.isFile() || !dataFile.exists()) {
                return "";
            }
            BufferedReader reader = null;
            try {
                InputStreamReader is = new InputStreamReader(new FileInputStream(dataFile), "UTF-8");
                reader = new BufferedReader(is);
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (!fileContent.toString().equals("")) {
                        fileContent.append("\r\n");
                    }
                    fileContent.append(line);
                }
                reader.close();
            } catch (Exception e) {
                throw new RuntimeException("IOException occurred. ", e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return fileContent.toString();
        }

        /**
         * 从assets文件夹读取数据
         *
         * @param context 上下文
         * @param name    文件名
         * @return 数据
         */
        static String readJsonFromAssets(Context context, String name) {
            String json = "";
            try {
                StringBuilder builder = new StringBuilder();
                InputStreamReader streamReader = new InputStreamReader(context.getAssets().open(name), "UTF-8");
                BufferedReader reader = new BufferedReader(streamReader);
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                    builder.append("");
                }
                json = builder.toString();
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return json;
        }

        /**
         * 从JSON中读取Frame数据
         *
         * @param json 数据
         * @return 集合里面存储的是每个笔顺对应的点数据
         */
        static List<List<Point>> extractFrame(String json) {
            List<List<Point>> mFrame = new ArrayList<>();
            try {
                JSONObject jsonObject = new JSONObject(json);
                JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);
                JSONArray out = data.getJSONArray("frame");
                for (int i = 0; i < out.length(); i++) {
                    List<Point> ps = new ArrayList<>();
                    JSONArray frame = out.getJSONArray(i);
                    for (int j = 0; j < frame.length(); j++) {
                        JSONArray pointArr = frame.getJSONArray(j);
                        Point p = new Point();
                        p.set(pointArr.getInt(0), pointArr.getInt(1));
                        ps.add(p);
                    }
                    mFrame.add(ps);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return mFrame;
        }

        /**
         * 从JSON中读取Fill数据
         *
         * @param json 数据
         * @return 多笔画对应的填充数据
         */
        static List<List<Point>> extractFill(String json) {
            List<List<Point>> mFrame = new ArrayList<>();
            try {
                JSONObject jsonObject = new JSONObject(json);
                JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);
                JSONArray out = data.getJSONArray("fill");
                for (int i = 0; i < out.length(); i++) {
                    List<Point> ps = new ArrayList<>();
                    JSONArray frame = out.getJSONArray(i);
                    for (int j = 0; j < frame.length(); j++) {
                        JSONArray pointArr = frame.getJSONArray(j);
                        Point p = new Point();
                        p.set(pointArr.getInt(0), pointArr.getInt(1));
                        ps.add(p);
                    }
                    mFrame.add(ps);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return mFrame;
        }

        /**
         * 读取笔画
         *
         * @param json 数据
         * @return 笔画集合,
         */
        static String getStrokeString(String json) {
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(json);
                JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);
                return data.getString("bihua_mingcheng");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return "";
        }

        /**
         * 将点连成线
         *
         * @param points 一系列点
         * @return 路径
         */
        static Path generatePathByPoint(List<Point> points) {
            Path path = new Path();
            for (int i = 0; i < points.size(); i++) {
                Point p = points.get(i);
                p = scalePoint(p);
                if (i == 0) {
                    path.moveTo(p.x, p.y);
                } else {
                    path.lineTo(p.x, p.y);
                }
            }
            path.close();
            return path;
        }

        /**
         * 将路径转变成不规则区域
         *
         * @param path 路径
         * @return 区域
         */
        static Region pathToRegion(Path path) {
            RectF bounds = new RectF();
            bounds.setEmpty();
            path.computeBounds(bounds, true);
            Region region = new Region();
            region.setPath(path, new Region((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom));
            return region;
        }

        /**
         * 分割List
         *
         * @param list       集合
         * @param startIndex 起点
         * @param endIndex   尾点
         * @return 子集合
         */
        static List<Point> subList(List<Point> list, int startIndex, int endIndex) {
            List<Point> subList = new ArrayList<>();
            if (startIndex < 0) {
                startIndex = 0;
            }
            if (endIndex >= list.size()) {
                endIndex = list.size() - 1;
            }
            for (int i = startIndex; i < list.size() && i < endIndex; i++) {
                Point p = list.get(i);
                subList.add(p);
            }
            //subList = list.subList(startIndex, endIndex);
            return subList;
        }

        /**
         * 缩放原数据坐标到布局大小
         *
         * @param point 坐标点
         * @return 缩放后的点
         */
        static Point scalePoint(Point point) {
            Point p = new Point();
            //760 是原数据默认宽高
            p.x = (int) (point.x / (760f / width));
            p.y = (int) (point.y / (760f / height));
            return p;
        }

        /**
         * 随机颜色
         *
         * @return 颜色值
         */
        static int randomColor() {
            Random random = new Random();
            return random.nextInt(200);
        }
    }
}