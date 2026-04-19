package com.acme.sample;


import com.acme.sample.custom.Utils;

public class Test {

    public static void main(String[] args) {
        var email = new EMail("a@b.de");
        var productId = new ProductId("fubar");
        System.out.println("Testing generated utils");
        System.out.println(Utils.toUpperCase(email));
        System.out.println(Utils.toUpperCase(productId));
    }

}
