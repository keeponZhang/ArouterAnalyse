package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.compiler.utils.Logger;
import com.alibaba.android.arouter.compiler.utils.TypeUtils;
import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.facade.enums.RouteType;
import com.alibaba.android.arouter.facade.model.RouteMeta;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.alibaba.android.arouter.compiler.utils.Consts.ACTIVITY;
import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_ROUTE;
import static com.alibaba.android.arouter.compiler.utils.Consts.FRAGMENT;
import static com.alibaba.android.arouter.compiler.utils.Consts.IPROVIDER_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.IROUTE_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.ITROUTE_ROOT;
import static com.alibaba.android.arouter.compiler.utils.Consts.KEY_MODULE_NAME;
import static com.alibaba.android.arouter.compiler.utils.Consts.METHOD_LOAD_INTO;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_PROVIDER;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_ROOT;
import static com.alibaba.android.arouter.compiler.utils.Consts.PACKAGE_OF_GENERATE_FILE;
import static com.alibaba.android.arouter.compiler.utils.Consts.SEPARATOR;
import static com.alibaba.android.arouter.compiler.utils.Consts.SERVICE;
import static com.alibaba.android.arouter.compiler.utils.Consts.WARNING_TIPS;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * A processor used for find route.
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/15 下午10:08
 */
@AutoService(Processor.class)
@SupportedOptions(KEY_MODULE_NAME)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({ANNOTATION_TYPE_ROUTE, ANNOTATION_TYPE_AUTOWIRED})
public class RouteProcessor extends AbstractProcessor {
    /**
     * 以组为 key，value 对应于该组下解析出的所有注解信息，Set 的实现为 TreeSet，会根据 path 来进行排序。
     */
    private Map<String, Set<RouteMeta>> groupMap = new HashMap<>(); // ModuleName and routeMeta.
    /**
     * 以根节点为 key，value 对应于创建的文件。
     */
    private Map<String, String> rootMap = new TreeMap<>();  // Map of root metas, used for generate class file in order.
    private Filer mFiler;       // File util, write class file into disk.
    private Logger logger;
    private Types types;
    private Elements elements;
    private TypeUtils typeUtils;
    private String moduleName = null;   // Module name, maybe its 'app' or others
    private TypeMirror iProvider = null;

    /**
     * Initializes the processor with the processing environment by
     * setting the {@code processingEnv} field to the value of the
     * {@code processingEnv} argument.  An {@code
     * IllegalStateException} will be thrown if this method is called
     * more than once on the same object.
     *
     * @param processingEnv environment to access facilities the tool framework
     *                      provides to the processor
     * @throws IllegalStateException if this method is called more than once.
     */
    /**
     * 通过 ProcessingEnvironment 初始化后面需要使用到的工具类，例如 Filer，Types 和 Elements。
     * @param processingEnv 提供解析注解所需的工具类。
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        //Filer：用于创建 Java 文件的工具类。
        mFiler = processingEnv.getFiler();                  // Generate class.
        //Types：用于操作类型的工具类。
        types = processingEnv.getTypeUtils();            // Get type utils.
        //Elements：用于处理 Element 的工具类。
        elements = processingEnv.getElementUtils();      // Get class meta.
        //将两个工具类进行封装。
        typeUtils = new TypeUtils(types, elements);
        //日志工具类。
        logger = new Logger(processingEnv.getMessager());   // Package the log utils.

        // Attempt to get user configuration [moduleName]
        //解析我们在 android 节点下配置的 moduleName，其为对应模块的名字。
        Map<String, String> options = processingEnv.getOptions();
        if (MapUtils.isNotEmpty(options)) {
            moduleName = options.get(KEY_MODULE_NAME);
        }
        //对 moduleName 进行处理。
        if (StringUtils.isNotEmpty(moduleName)) {
            moduleName = moduleName.replaceAll("[^0-9a-zA-Z_]+", "");

            logger.info("The user has configuration the module name, it was [" + moduleName + "]");
        } else {
            //如果没有配置 moduleName，那么会抛出异常。
            logger.error("These no module name, at 'build.gradle', like :\n" +
                    "apt {\n" +
                    "    arguments {\n" +
                    "        moduleName project.getName();\n" +
                    "    }\n" +
                    "}\n");
            throw new RuntimeException("ARouter::Compiler >>> No module name, for more information, look at gradle log.");
        }
        //获取 IProvider 的 TypeMirror，它包含了 IProvider 的所有信息。
        iProvider = elements.getTypeElement(Consts.IPROVIDER).asType();

        logger.info(">>> RouteProcessor init. <<<");
    }

    /**
     * {@inheritDoc}
     *
     * @param annotations 使用了支持处理注解的集合
     * @param roundEnv 表示当前或者之前的运行环境，可以通过对象查找到的注解
     * @return true 表示后续处理器不会再处理
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (CollectionUtils.isNotEmpty(annotations)) {
            //获得所有被 @Route 注解的元素。
            Set<? extends Element> routeElements = roundEnv.getElementsAnnotatedWith(Route.class);
            try {
                logger.info(">>> Found routes, start... <<<");
                //开始处理。
                this.parseRoutes(routeElements);

            } catch (Exception e) {
                logger.error(e);
            }
            // int i = 0/0;
            return true;
        }

        return false;
    }

    private void parseRoutes(Set<? extends Element> routeElements) throws IOException {
        if (CollectionUtils.isNotEmpty(routeElements)) {
            // Perpare the type an so on.

            logger.info(">>> Found routes, size is " + routeElements.size() + " <<<");
            //清空信息。
            rootMap.clear();
            //1.获得 Activity、Service、Fragment 和 FragmentV4 的信息，这些是定义在 Android SDK 当中的。
            TypeMirror type_Activity = elements.getTypeElement(ACTIVITY).asType();
            TypeMirror type_Service = elements.getTypeElement(SERVICE).asType();
            TypeMirror fragmentTm = elements.getTypeElement(FRAGMENT).asType();
            TypeMirror fragmentTmV4 = elements.getTypeElement(Consts.FRAGMENT_V4).asType();

            // Interface of ARouter
            //2.获得 IRouteGroup 和 IProviderGroup 的信息，这些接口是定义在 arouter-api 当中的模板。
            TypeElement type_IRouteGroup = elements.getTypeElement(IROUTE_GROUP);
            TypeElement type_IProviderGroup = elements.getTypeElement(IPROVIDER_GROUP);

            //3.RouteMeta 和 RouteType 的信息。
            ClassName routeMetaCn = ClassName.get(RouteMeta.class);
            ClassName routeTypeCn = ClassName.get(RouteType.class);

            /*
               Build input type, format as :

               ```Map<String, Class<? extends IRouteGroup>>```
             */
            //4. 定义函数形参的类型为 Map<String, Class<? extends IRouteGroup>>。
            ParameterizedTypeName inputMapTypeOfRoot = ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ParameterizedTypeName.get(
                            ClassName.get(Class.class),
                            WildcardTypeName.subtypeOf(ClassName.get(type_IRouteGroup))
                    )
            );

            /*
              ```Map<String, RouteMeta>```
             */
            //5. 定义函数形参的类型为 Map<String, RouteMeta>。
            ParameterizedTypeName inputMapTypeOfGroup = ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(RouteMeta.class)
            );

            /*
              Build input param name.
             */
            //6. 定义函数的形参类型 & 形参名字。
            //6.1 Map<String, Class<? extends IRouteGroup>> routes
            ParameterSpec rootParamSpec = ParameterSpec.builder(inputMapTypeOfRoot, "routes").build();
            //6.2 Map<String, RouteMeta> atlas
            ParameterSpec groupParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "atlas").build();
            //6.3 Map<String, RouteMeta> providers
            ParameterSpec providerParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "providers").build();  // Ps. its param type same as groupParamSpec!

            /*
              Build method : 'loadInto'
             */
            //7.定义函数的声明为 public void loadInto(Map<String, Class<? extends IRouteGroup>> routes)。
            MethodSpec.Builder loadIntoMethodOfRootBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(rootParamSpec);

            //  Follow a sequence, find out metas of group first, generate java file, then statistics them as root.
            //8.遍历所有的被 @Route 注解的 Element
            for (Element element : routeElements) {
                //获得该元素的类型信息。
                TypeMirror tm = element.asType();
                //获得该元素的注解。
                Route route = element.getAnnotation(Route.class);
                RouteMeta routeMete = null;
                //Activity 的子类。
                if (types.isSubtype(tm, type_Activity)) {                 // Activity
                    logger.info(">>> Found activity route: " + tm.toString() + " <<<");

                    // Get all fields annotation by @Autowired
                    Map<String, Integer> paramsType = new HashMap<>();
                    //获得其所有被 @Autowired 注解的成员变量。
                    for (Element field : element.getEnclosedElements()) {
                        //不处理 IProvider 的子类。
                        if (field.getKind().isField() && field.getAnnotation(Autowired.class) != null && !types.isSubtype(field.asType(), iProvider)) {
                            // It must be field, then it has annotation, but it not be provider.
                            Autowired paramConfig = field.getAnnotation(Autowired.class);
                            //将所有被 @Autowired 注解的相关信息放到 map 当中。
                            paramsType.put(StringUtils.isEmpty(paramConfig.name()) ? field.getSimpleName().toString() : paramConfig.name(), typeUtils.typeExchange(field));
                        }
                    }
                    routeMete = new RouteMeta(route, element, RouteType.ACTIVITY, paramsType);

                } else if (types.isSubtype(tm, iProvider)) {         // IProvider
                    //IProvider 的子类。
                    logger.info(">>> Found provider route: " + tm.toString() + " <<<");
                    routeMete = new RouteMeta(route, element, RouteType.PROVIDER, null);
                } else if (types.isSubtype(tm, type_Service)) {           // Service
                    //IProvider 的子类。
                    logger.info(">>> Found service route: " + tm.toString() + " <<<");
                    routeMete = new RouteMeta(route, element, RouteType.parse(SERVICE), null);
                } else if (types.isSubtype(tm, fragmentTm) || types.isSubtype(tm, fragmentTmV4)) {
                    //Fragment 或者 FragmentV4 的子类。
                    logger.info(">>> Found fragment route: " + tm.toString() + " <<<");
                    routeMete = new RouteMeta(route, element, RouteType.parse(FRAGMENT), null);
                }
                //对其按 group 进行分组。
                categories(routeMete);
                // if (StringUtils.isEmpty(moduleName)) {   // Hasn't generate the module name.
                //     moduleName = ModuleUtils.generateModuleName(element, logger);
                // }
            }
            //9. 定义函数的声明为 public void loadInto(Map<String, RouteMeta> providers)。
            MethodSpec.Builder loadIntoMethodOfProviderBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(providerParamSpec);

            // Start generate java source, structure is divided into upper and lower levels, used for demand initialization.
            //10. 创建 Java 源代码，遍历所有的 group。
            for (Map.Entry<String, Set<RouteMeta>> entry : groupMap.entrySet()) {
                String groupName = entry.getKey();
                //定义函数的声明为 public void loadInto(Map<String, RouteMeta> atlas)。
                MethodSpec.Builder loadIntoMethodOfGroupBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(groupParamSpec);

                // Build group method body
                //遍历该 group 下所有的 RouteMeta。
                Set<RouteMeta> groupData = entry.getValue();
                for (RouteMeta routeMeta : groupData) {
                    switch (routeMeta.getType()) {
                        //填充函数体。
                        case PROVIDER:  // Need cache provider's super class
                            List<? extends TypeMirror> interfaces = ((TypeElement) routeMeta.getRawType()).getInterfaces();
                            //IProvider。
                            for (TypeMirror tm : interfaces) {
                                if (types.isSameType(tm, iProvider)) {   // Its implements iProvider interface himself.
                                    // This interface extend the IProvider, so it can be used for mark provider
                                    //添加 providers.put(..) 函数体。
                                    loadIntoMethodOfProviderBuilder.addStatement(
                                            "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                            (routeMeta.getRawType()).toString(),
                                            routeMetaCn,
                                            routeTypeCn,
                                            ClassName.get((TypeElement) routeMeta.getRawType()),
                                            routeMeta.getPath(),
                                            routeMeta.getGroup());
                                } else if (types.isSubtype(tm, iProvider)) {
                                    //IProvider 的子类。
                                    // This interface extend the IProvider, so it can be used for mark provider
                                    //添加 providers.put(..) 函数体。
                                    loadIntoMethodOfProviderBuilder.addStatement(
                                            "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                            tm.toString(),    // So stupid, will duplicate only save class name.
                                            routeMetaCn,
                                            routeTypeCn,
                                            ClassName.get((TypeElement) routeMeta.getRawType()),
                                            routeMeta.getPath(),
                                            routeMeta.getGroup());
                                }
                            }
                            break;
                        default:
                            break;
                    }

                    // Make map body for paramsType
                    StringBuilder mapBodyBuilder = new StringBuilder();
                    Map<String, Integer> paramsType = routeMeta.getParamsType();
                    if (MapUtils.isNotEmpty(paramsType)) {
                        for (Map.Entry<String, Integer> types : paramsType.entrySet()) {
                            mapBodyBuilder.append("put(\"").append(types.getKey()).append("\", ").append(types.getValue()).append("); ");
                        }
                    }
                    String mapBody = mapBodyBuilder.toString();
                    //填充函数体。
                    loadIntoMethodOfGroupBuilder.addStatement(
                            "atlas.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, " + (StringUtils.isEmpty(mapBody) ? null : ("new java.util.HashMap<String, Integer>(){{" + mapBodyBuilder.toString() + "}}")) + ", " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                            routeMeta.getPath(),
                            routeMetaCn,
                            routeTypeCn,
                            ClassName.get((TypeElement) routeMeta.getRawType()),
                            routeMeta.getPath().toLowerCase(),
                            routeMeta.getGroup().toLowerCase());
                }

                // Generate groups
                String groupFileName = NAME_OF_GROUP + groupName;
                //关键点1：每一个 group 创建一个 Java 文件，其类名为 Arouter$$Group$$组名，函数名为 public void loadInto(Map<String, RouteMeta> atlas)
                JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                        TypeSpec.classBuilder(groupFileName)
                                .addJavadoc(WARNING_TIPS)
                                .addSuperinterface(ClassName.get(type_IRouteGroup))
                                .addModifiers(PUBLIC)
                                .addMethod(loadIntoMethodOfGroupBuilder.build())
                                .build()
                ).build().writeTo(mFiler);

                logger.info(">>> Generated group: " + groupName + "<<<");
                rootMap.put(groupName, groupFileName);
            }

            if (MapUtils.isNotEmpty(rootMap)) {
                // Generate root meta by group name, it must be generated before root, then I can find out the class of group.
                for (Map.Entry<String, String> entry : rootMap.entrySet()) {
                    loadIntoMethodOfRootBuilder.addStatement("routes.put($S, $T.class)", entry.getKey(), ClassName.get(PACKAGE_OF_GENERATE_FILE, entry.getValue()));
                }
            }

            // Wirte provider into disk
            //关键点2：创建 Java 文件，类名为 Arouter$$Providers$$moduleName，函数名为 public void loadInto(Map<String, RouteMeta> providers)，存放 PROVIDER 类型的节点。
            String providerMapFileName = NAME_OF_PROVIDER + SEPARATOR + moduleName;
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    TypeSpec.classBuilder(providerMapFileName)
                            .addJavadoc(WARNING_TIPS)
                            .addSuperinterface(ClassName.get(type_IProviderGroup))
                            .addModifiers(PUBLIC)
                            .addMethod(loadIntoMethodOfProviderBuilder.build())
                            .build()
            ).build().writeTo(mFiler);

            logger.info(">>> Generated provider map, name is " + providerMapFileName + " <<<");

            // Write root meta into disk.
            //关键点3：创建 Java 文件，类名为 Arouter$$Root$$moduleName，函数名为 public void loadInto(Map<String, Class<? extends IRouteGroup>> routes)
            String rootFileName = NAME_OF_ROOT + SEPARATOR + moduleName;
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    TypeSpec.classBuilder(rootFileName)
                            .addJavadoc(WARNING_TIPS)
                            .addSuperinterface(ClassName.get(elements.getTypeElement(ITROUTE_ROOT)))
                            .addModifiers(PUBLIC)
                            .addMethod(loadIntoMethodOfRootBuilder.build())
                            .build()
            ).build().writeTo(mFiler);

            logger.info(">>> Generated root, name is " + rootFileName + " <<<");
        }
    }

    /**
     * 对 @Route 注解的类进行分类。
     * @param routeMete
     */
    private void categories(RouteMeta routeMete) {
        if (routeVerify(routeMete)) {
            logger.info(">>> Start categories, group = " + routeMete.getGroup() + ", path = " + routeMete.getPath() + " <<<");
            Set<RouteMeta> routeMetas = groupMap.get(routeMete.getGroup());
            if (CollectionUtils.isEmpty(routeMetas)) {
                Set<RouteMeta> routeMetaSet = new TreeSet<>(new Comparator<RouteMeta>() {
                    @Override
                    public int compare(RouteMeta r1, RouteMeta r2) {
                        try {
                            return r1.getPath().compareTo(r2.getPath());
                        } catch (NullPointerException npe) {
                            logger.error(npe.getMessage());
                            return 0;
                        }
                    }
                });
                routeMetaSet.add(routeMete);
                groupMap.put(routeMete.getGroup(), routeMetaSet);
            } else {
                routeMetas.add(routeMete);
            }
        } else {
            logger.warning(">>> Route meta verify error, group is " + routeMete.getGroup() + " <<<");
        }
    }

    /**
     * 验证 RouteMeta，要求 @Route 指定的 name 不为空，并且要使用 / 作为开头。
     *
     * 并解析 RouteMeta 中的 group，默认是使用 / 后的第一个字段，如果指定了 group，那么就使用 group 的值。
     * @param meta
     * @return 验证正确。
     */
    private boolean routeVerify(RouteMeta meta) {
        String path = meta.getPath();

        if (StringUtils.isEmpty(path) || !path.startsWith("/")) {   // The path must be start with '/' and not empty!
            return false;
        }

        if (StringUtils.isEmpty(meta.getGroup())) { // Use default group(the first word in path)
            try {
                String defaultGroup = path.substring(1, path.indexOf("/", 1));
                if (StringUtils.isEmpty(defaultGroup)) {
                    return false;
                }

                meta.setGroup(defaultGroup);
                return true;
            } catch (Exception e) {
                logger.error("Failed to extract default group! " + e.getMessage());
                return false;
            }
        }

        return true;
    }
}
