package com.example.handwriting.util;

import android.content.Context;
import android.graphics.Point;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class WritingUtil {

    public static String urlEncode(String c) throws Exception {
        String encode = URLEncoder.encode(c, "utf-8");
        encode = encode.replaceAll("%", "");
        return encode.toLowerCase();
    }

    public static String getAssestFileContent(Context context, String name) {
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

    public static List<List<Point>> parseJsonFrame(String json) {
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

    public static List<List<Point>> parseJsonFill(String json) {
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
}
