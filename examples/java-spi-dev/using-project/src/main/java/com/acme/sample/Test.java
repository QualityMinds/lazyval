package com.acme.sample;


import com.acme.sample.test.EMailUtils;
import com.acme.sample.test.ProductIdUtils;
import com.acme.sample.test.Utils;

public class Test {

    public static void main(String[] args) {
        var email = new EMail("a@b.de");
        var productId = new ProductId("fubar");
        System.out.println("Testing dedicated Utils");
        System.out.println(EMailUtils.toUpperCase(email));
        System.out.println(ProductIdUtils.toUpperCase(productId));
        System.out.println("Testing all-in-one Utils");
        System.out.println(Utils.toUpperCase(email));
        System.out.println(Utils.toUpperCase(productId));
    }

}
