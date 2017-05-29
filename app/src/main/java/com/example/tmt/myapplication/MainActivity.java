package com.example.tmt.myapplication;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initData();
    }


    public void initData() {
        List<Product> productList = new ArrayList<>();
        productList.add(new Product("1", "true"));
        productList.add(new Product("1", "false"));
        productList.add(new Product("1", "true"));
        productList.add(new Product("1", "false"));
        productList.add(new Product("1", "false"));
        productList.add(new Product("1", "false"));
        productList.add(new Product("1", "false"));
        productList.add(new Product("1", "true"));
        productList.add(new Product("1", "false"));
        productList.add(new Product("1", "false"));


        List<Product> highlight = new ArrayList<>();
        List<Product> nonHighlight = new ArrayList<>();

        for (int i = 0; i < productList.size(); i++) {
            if (productList.get(i).isHighlight()) {
                highlight.add(productList.get(i));
            } else {
                nonHighlight.add(productList.get(i));
            }
        }

        List<List<Product>> listData = new ArrayList<>();

        if (nonHighlight.size() > 2) {
            for (int i = 0; i < nonHighlight.size(); i = i + 2) {
                List<Product> listItem = new ArrayList<>();
                listItem.add(nonHighlight.get(i));
                if (i + 1 < nonHighlight.size()) {
                    listItem.add(nonHighlight.get(i + 1));
                }
                listData.add(listItem);
            }
        } else {
            listData.add(nonHighlight);
        }

        for (int i = 0; i < highlight.size(); i++) {
            if (listData.size() > i) {
                List<Product> listItem = listData.get(i);
                listItem.add(highlight.get(i));
                listData.set(i, listItem);
            } else {
                List<Product> listItem = new ArrayList<>();
                listItem.add(highlight.get(i));
                listData.add(listItem);
            }
        }

        Log.d("Init Data", listData.size() + "");
    }


}
