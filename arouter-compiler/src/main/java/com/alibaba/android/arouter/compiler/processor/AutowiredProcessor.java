package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.compiler.utils.Logger;
import com.alibaba.android.arouter.compiler.utils.TypeUtils;
import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.enums.TypeKind;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.ISYRINGE;
import static com.alibaba.android.arouter.compiler.utils.Consts.JSON_SERVICE;
import static com.alibaba.android.arouter.compiler.utils.Consts.KEY_MODULE_NAME;
import static com.alibaba.android.arouter.compiler.utils.Consts.METHOD_INJECT;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.WARNING_TIPS;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Processor used to create autowired helper
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/20 下午5:56
 */
@AutoService(Processor.class)
@SupportedOptions(KEY_MODULE_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({ANNOTATION_TYPE_AUTOWIRED})
public class AutowiredProcessor extends AbstractProcessor {
    private Filer mFiler;       // File util, write class file into disk.
    private Logger logger;
    private Types types;
    private TypeUtils typeUtils;
    private Elements elements;
    private Map<TypeElement, List<Element>> parentAndChild = new HashMap<>();   // Contain field need autowired and his super class.
    private static final ClassName ARouterClass = ClassName.get("com.alibaba.android.arouter.launcher", "ARouter");
    private static final ClassName AndroidLog = ClassName.get("android.util", "Log");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        mFiler = processingEnv.getFiler();                  // Generate class.
        types = processingEnv.getTypeUtils();            // Get type utils.
        elements = processingEnv.getElementUtils();      // Get class meta.

        typeUtils = new TypeUtils(types, elements);

        logger = new Logger(processingEnv.getMessager());   // Package the log utils.

        logger.info(">>> AutowiredProcessor init. <<<");
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (CollectionUtils.isNotEmpty(set)) {
            try {
                logger.info(">>> Found autowired field, start... <<<");
                categories(roundEnvironment.getElementsAnnotatedWith(Autowired.class));
                generateHelper();

            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }
    /**
     * 开始进行解析。
     * @throws IOException IO 异常。
     * @throws IllegalAccessException 不满足状态的异常。
     */
    private void generateHelper() throws IOException, IllegalAccessException {
        TypeElement type_ISyringe = elements.getTypeElement(ISYRINGE);
        TypeElement type_JsonService = elements.getTypeElement(JSON_SERVICE);
        TypeMirror iProvider = elements.getTypeElement(Consts.IPROVIDER).asType();
        TypeMirror activityTm = elements.getTypeElement(Consts.ACTIVITY).asType();
        TypeMirror fragmentTm = elements.getTypeElement(Consts.FRAGMENT).asType();
        TypeMirror fragmentTmV4 = elements.getTypeElement(Consts.FRAGMENT_V4).asType();

        // Build input param name.
        //1. 函数的形参类型为 Object，形参名为 target。
        ParameterSpec objectParamSpec = ParameterSpec.builder(TypeName.OBJECT, "target").build();
        //2.遍历所有拥有 @Autowired 注解的元素。
        if (MapUtils.isNotEmpty(parentAndChild)) {
            for (Map.Entry<TypeElement, List<Element>> entry : parentAndChild.entrySet()) {
                // Build method : 'inject'
                //2.1 方法名为 inject
                MethodSpec.Builder injectMethodBuilder = MethodSpec.methodBuilder(METHOD_INJECT)
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(objectParamSpec);

                TypeElement parent = entry.getKey();
                List<Element> childs = entry.getValue();

                String qualifiedName = parent.getQualifiedName().toString();
                String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
                String fileName = parent.getSimpleName() + NAME_OF_AUTOWIRED;

                logger.info(">>> Start process " + childs.size() + " field in " + parent.getSimpleName() + " ... <<<");
                //2.2 类继承于 ISyringe。
                TypeSpec.Builder helper = TypeSpec.classBuilder(fileName)
                        .addJavadoc(WARNING_TIPS)
                        .addSuperinterface(ClassName.get(type_ISyringe))
                        .addModifiers(PUBLIC);
                //2.3 类中包含一个类型为 SerializationService，名称为 serializationService 的成员变量。
                FieldSpec jsonServiceField = FieldSpec.builder(TypeName.get(type_JsonService.asType()), "serializationService", Modifier.PRIVATE).build();
                helper.addField(jsonServiceField);
                //2.4 对 Object 类型的形参进行强制转型。
                injectMethodBuilder.addStatement("serializationService = $T.getInstance().navigation($T.class)", ARouterClass, ClassName.get(type_JsonService));
                injectMethodBuilder.addStatement("$T substitute = ($T)target", ClassName.get(parent), ClassName.get(parent));

                // Generate method body, start inject.
                //2.5 开始对成员变量遍历进行赋值。
                for (Element element : childs) {
                    Autowired fieldConfig = element.getAnnotation(Autowired.class);
                    String fieldName = element.getSimpleName().toString();
                    if (types.isSubtype(element.asType(), iProvider)) {  // It's provider
                        if ("".equals(fieldConfig.name())) {    // User has not set service path, then use byType.
                            //没有指定名称，通过类型来获取。
                            // Getter
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = $T.getInstance().navigation($T.class)",
                                    ARouterClass,
                                    ClassName.get(element.asType())
                            );
                        } else {    // use byName
                            // Getter
                            //指定了名称，通过名称来获取。
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = ($T)$T.getInstance().build($S).navigation();",
                                    ClassName.get(element.asType()),
                                    ARouterClass,
                                    fieldConfig.name()
                            );
                        }
                        //异常情况处理。
                        // Validater
                        if (fieldConfig.required()) {
                            injectMethodBuilder.beginControlFlow("if (substitute." + fieldName + " == null)");
                            injectMethodBuilder.addStatement(
                                    "throw new RuntimeException(\"The field '" + fieldName + "' is null, in class '\" + $T.class.getName() + \"!\")", ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        }
                    } else {    // It's normal intent value
                        //2.5.2 普通的成员变量。
                        String originalValue = "substitute." + fieldName;
                        String statement = "substitute." + fieldName + " = substitute.";
                        boolean isActivity = false;
                        //对 Activity 和 Fragment 进行不同的处理，分别使用 getIntent 和 getArguments 来获取参数。
                        if (types.isSubtype(parent.asType(), activityTm)) {  // Activity, then use getIntent()
                            isActivity = true;
                            statement += "getIntent().";
                        } else if (types.isSubtype(parent.asType(), fragmentTm) || types.isSubtype(parent.asType(), fragmentTmV4)) {   // Fragment, then use getArguments()
                            statement += "getArguments().";
                        } else {
                            throw new IllegalAccessException("The field [" + fieldName + "] need autowired from intent, its parent must be activity or fragment!");
                        }

                        statement = buildStatement(originalValue, statement, typeUtils.typeExchange(element), isActivity);
                        if (statement.startsWith("serializationService.")) {   // Not mortals
                            //需要通过序列化进行赋值的对象。
                            injectMethodBuilder.beginControlFlow("if (null != serializationService)");
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = " + statement,
                                    (StringUtils.isEmpty(fieldConfig.name()) ? fieldName : fieldConfig.name()),
                                    ClassName.get(element.asType())
                            );
                            injectMethodBuilder.nextControlFlow("else");
                            injectMethodBuilder.addStatement(
                                    "$T.e(\"" + Consts.TAG + "\", \"You want automatic inject the field '" + fieldName + "' in class '$T' , then you should implement 'SerializationService' to support object auto inject!\")", AndroidLog, ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        } else {
                            //普通赋值的对象。
                            injectMethodBuilder.addStatement(statement, StringUtils.isEmpty(fieldConfig.name()) ? fieldName : fieldConfig.name());
                        }

                        // Validator
                        if (fieldConfig.required() && !element.asType().getKind().isPrimitive()) {  // Primitive wont be check.
                            injectMethodBuilder.beginControlFlow("if (null == substitute." + fieldName + ")");
                            injectMethodBuilder.addStatement(
                                    "$T.e(\"" + Consts.TAG + "\", \"The field '" + fieldName + "' is null, in class '\" + $T.class.getName() + \"!\")", AndroidLog, ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        }
                    }
                }

                helper.addMethod(injectMethodBuilder.build());

                // Generate autowire helper
                JavaFile.builder(packageName, helper.build()).build().writeTo(mFiler);

                logger.info(">>> " + parent.getSimpleName() + " has been processed, " + fileName + " has been generated. <<<");
            }

            logger.info(">>> Autowired processor stop. <<<");
        }
    }
    /**
     * 根据成员变量的类型进行区分。
     * @param originalValue
     * @param statement
     * @param type
     * @param isActivity
     * @return
     */
    private String buildStatement(String originalValue, String statement, int type, boolean isActivity) {
        if (type == TypeKind.BOOLEAN.ordinal()) {
            statement += (isActivity ? ("getBooleanExtra($S, " + originalValue + ")") : ("getBoolean($S)"));
        } else if (type == TypeKind.BYTE.ordinal()) {
            statement += (isActivity ? ("getByteExtra($S, " + originalValue + "") : ("getByte($S)"));
        } else if (type == TypeKind.SHORT.ordinal()) {
            statement += (isActivity ? ("getShortExtra($S, " + originalValue + ")") : ("getShort($S)"));
        } else if (type == TypeKind.INT.ordinal()) {
            statement += (isActivity ? ("getIntExtra($S, " + originalValue + ")") : ("getInt($S)"));
        } else if (type == TypeKind.LONG.ordinal()) {
            statement += (isActivity ? ("getLongExtra($S, " + originalValue + ")") : ("getLong($S)"));
        }else if(type == TypeKind.CHAR.ordinal()){
            statement += (isActivity ? ("getCharExtra($S, " + originalValue + ")") : ("getChar($S)"));
        } else if (type == TypeKind.FLOAT.ordinal()) {
            statement += (isActivity ? ("getFloatExtra($S, " + originalValue + ")") : ("getFloat($S)"));
        } else if (type == TypeKind.DOUBLE.ordinal()) {
            statement += (isActivity ? ("getDoubleExtra($S, " + originalValue + ")") : ("getDouble($S)"));
        } else if (type == TypeKind.STRING.ordinal()) {
            statement += (isActivity ? ("getStringExtra($S)") : ("getString($S)"));
        } else if (type == TypeKind.PARCELABLE.ordinal()) {
            statement += (isActivity ? ("getParcelableExtra($S)") : ("getParcelable($S)"));
        } else if (type == TypeKind.OBJECT.ordinal()) {
            statement = "serializationService.parseObject(substitute." + (isActivity ? "getIntent()." : "getArguments().") + (isActivity ? "getStringExtra($S)" : "getString($S)") + ", new com.alibaba.android.arouter.facade.model.TypeWrapper<$T>(){}.getType())";
        }

        return statement;
    }

    /**
     * Categories field, find his papa.
     *
     * @param elements Field need autowired
     */
    /**
     * 进行分类处理。
     * @param elements
     * @throws IllegalAccessException
     */
    private void
    categories(Set<? extends Element> elements) throws IllegalAccessException {
        if (CollectionUtils.isNotEmpty(elements)) {
            for (Element element : elements) {
                TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
                //不允许声明为 private。
                if (element.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new IllegalAccessException("The inject fields CAN NOT BE 'private'!!! please check field ["
                            + element.getSimpleName() + "] in class [" + enclosingElement.getQualifiedName() + "]");
                }

                //一个类可能注入几个
                if (parentAndChild.containsKey(enclosingElement)) { // Has categries
                    parentAndChild.get(enclosingElement).add(element);
                } else {
                    List<Element> childs = new ArrayList<>();
                    childs.add(element);
                    parentAndChild.put(enclosingElement, childs);
                }
            }

            logger.info("categories finished.");
        }
    }
}
