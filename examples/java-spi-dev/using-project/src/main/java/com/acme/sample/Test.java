package com.acme.sample;

import com.acme.sample.test.EMailUtils;

public class Test {

    public static void main(String[] args) {
        var email = new EMail("a@b.de");
        System.out.println(EMailUtils.toUppercase(email));
    }

}
