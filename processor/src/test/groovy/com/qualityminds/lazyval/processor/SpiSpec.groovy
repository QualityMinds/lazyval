package com.qualityminds.lazyval.processor


import com.qualityminds.lazyval.processor.spi.TypeName
import spock.lang.Specification
import spock.lang.Unroll


class SpiSpec extends Specification {

    @Unroll("static TypeName '#staticFieldDeclaration' corresponds to Java type '#javaName'")
    void "Static Fields correctly mapped to Java types"(){
        expect:
        staticFieldDeclaration == javaName

        where:
        staticFieldDeclaration  | javaClass
        TypeName.BOOLEAN        | boolean.class
        TypeName.BOOLEAN_BOXED  | Boolean.class
        TypeName.BYTE           | byte.class
        TypeName.BYTE_BOXED     | Byte.class
        TypeName.SHORT          | short.class
        TypeName.SHORT_BOXED    | Short.class
        TypeName.INT            | int.class
        TypeName.INT_BOXED      | Integer.class
        TypeName.LONG           | long.class
        TypeName.LONG_BOXED     | Long.class
        TypeName.CHAR           | char.class
        TypeName.CHAR_BOXED     | Character.class
        TypeName.FLOAT          | float.class
        TypeName.FLOAT_BOXED    | Float.class
        TypeName.DOUBLE         | double .class
        TypeName.DOUBLE_BOXED   | Double.class
        javaName = new TypeName(javaClass.getSimpleName())
    }

    @Unroll("TypeName for '#typeName' #answer")
    void "Determine if boxed primitive"(){
        expect:
        typeName.isBoxedPrimitive() == isBoxed

        where:
        typeName  | isBoxed
        TypeName.BOOLEAN        | false
        TypeName.BOOLEAN_BOXED  | true
        TypeName.BYTE           | false
        TypeName.BYTE_BOXED     | true
        TypeName.SHORT          | false
        TypeName.SHORT_BOXED    | true
        TypeName.INT            | false
        TypeName.INT_BOXED      | true
        TypeName.LONG           | false
        TypeName.LONG_BOXED     | true
        TypeName.CHAR           | false
        TypeName.CHAR_BOXED     | true
        TypeName.FLOAT          | false
        TypeName.FLOAT_BOXED    | true
        TypeName.DOUBLE         | false
        TypeName.DOUBLE_BOXED   | true
        answer = isBoxed ? 'is a boxed primitive' : 'is not boxed'
    }

    @Unroll("Primitive '#unboxed' is correctly boxed to '#expected.simpleName'")
    void "Primitive boxing"(){
        given:
        def name = new TypeName(unboxed.toString())

        expect:
        name.box() == expected

        where:
        unboxed       | expected
        boolean.class | TypeName.BOOLEAN_BOXED
        byte.class    | TypeName.BYTE_BOXED
        short.class   | TypeName.SHORT_BOXED
        int.class     | TypeName.INT_BOXED
        long.class    | TypeName.LONG_BOXED
        char.class    | TypeName.CHAR_BOXED
        float.class   | TypeName.FLOAT_BOXED
        double.class  | TypeName.DOUBLE_BOXED
    }

    @Unroll("Boxed '#boxedName.simpleName' is correctly unboxed to '#expected.simpleName'")
    void "Unboxing to primitive"(){
        expect:
        boxedName.unbox() == expected

        where:
        boxedName              | unboxed
        TypeName.BOOLEAN_BOXED | boolean.class
        TypeName.BYTE_BOXED    | byte.class
        TypeName.SHORT_BOXED   | short.class
        TypeName.INT_BOXED     | int.class
        TypeName.LONG_BOXED    | long.class
        TypeName.CHAR_BOXED    | char.class
        TypeName.FLOAT_BOXED   | float.class
        TypeName.DOUBLE_BOXED  | double.class
        expected = new TypeName(unboxed.getSimpleName())
    }

    void "Non-primitive boxing fails"(){
        when:
        new TypeName("SomeClass").box()

        then:
        thrown(UnsupportedOperationException)
    }

    void "Non-primitive unboxing fails"(){
        when:
        new TypeName("SomeClass").unbox()

        then:
        thrown(UnsupportedOperationException)
    }

    void "TypeName '#name' maps to filename '#expectedFileName' removing '.'"(){
        given:
        def typeName = new TypeName(name)

        expect:
        typeName.name() == expectedFileName

        where:
        name                        || expectedFileName
        "SomeType"                  || "SomeType"
        "EnclosingType.InnerType"   || "EnclosingTypeInnerType"
    }
}