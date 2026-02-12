package com.acme.sample;

import de.qualityminds.lazyval.LazyValue;

import java.util.regex.Pattern;

@LazyValue
public record EMail (String value){

    // a very simple email regex (don't use this)
    private static final Pattern REGEX = Pattern.compile("^(.+)@(\\S+)$");

    public EMail {
        if(value.length() > 254){
            throw new IllegalArgumentException("EMail must not exceed 254 characters");
        }
        if(!REGEX.matcher(value).matches()){
            throw new IllegalArgumentException("Invalid EMail format");
        }
    }
}
