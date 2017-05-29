package com.example.tmt.myapplication;

/**
 * Created by tmt on 29/05/2017.
 */

public class Product {

    String productName;
    String highlight;

    public boolean isHighlight() {
        return highlight.equals(String.valueOf(false));
    }


    public Product(String productName, String highlight) {
        this.productName = productName;
        this.highlight = highlight;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getHighlight() {
        return highlight;
    }

    public void setHighlight(String highlight) {
        this.highlight = highlight;
    }
}
