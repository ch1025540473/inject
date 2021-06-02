package com.hang.inject

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.transform.TransformException

class InjectTransform extends Transform {

    Project mProject;

    InjectTransform() {
    }

    InjectTransform(Project mProject) {
        this.mProject = mProject;
    }

    @Override
    public String getName() {
        return "chenhang";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        for (TransformInput transformInput : transformInvocation.getInputs()) {
            for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                File directoryFile = new File(directoryInput.getFile().getAbsolutePath());
                System.out.println("[InjectTransform] Begin to inject: " + directoryFile.getAbsolutePath())
                if (directoryFile.isDirectory()) {
                    directoryFile.eachFileRecurse { File file ->
                        println("[InjectTransform] Directory output dest--sss: $file.name")
                        if (file.name.endsWith("Activity.class")) {
                            println("[InjectTransform] Directory output dest--aaa: $file.name")
                            handleClass(file, directoryFile.getAbsolutePath())

                            // 获取输出目录
                            def dest = transformInvocation.outputProvider.getContentLocation(directoryInput.name,
                                    directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)

                            println("[InjectTransform] Directory output dest: $dest.absolutePath")
                            // 将input的目录复制到output指定目录
                            FileUtils.copyDirectory(directoryInput.file, dest)
                        }
                    }
                }
            }

            // jar文件，如第三方依赖
            transformInput.jarInputs.each { jarInput ->
                def dest = transformInvocation.outputProvider.getContentLocation(jarInput.name,
                        jarInput.contentTypes, jarInput.scopes, Format.JAR)
                FileUtils.copyFile(jarInput.file, dest)
            }
        }

    }


    private void handleClass(File clsFile, String originPath) {
        String cls = new File(originPath).relativePath(clsFile).replace('/', '.')
        cls = cls.substring(0, cls.lastIndexOf('.class'))
        println("[Inject] Cls: $cls")

        ClassPool pool = ClassPool.getDefault()
        // 加入当前路径
        pool.appendClassPath(originPath)
        // project.android.bootClasspath 加入android.jar，不然找不到android相关的所有类
        pool.appendClassPath(mProject.android.bootClasspath[0].toString())
        // 引入android.os.Bundle包，因为onCreate方法参数有Bundle
        pool.importPackage('android.os.Bundle')
        pool.importPackage('android.os.SystemClock')
        pool.importPackage('android.util.Log')

        CtClass ctClass = pool.getCtClass(cls)
        if (ctClass.isFrozen()) {
            ctClass.defrost();
        }

        CtMethod ctMethod = ctClass.getDeclaredMethod("onCreate")

        // 方法尾插入
        ctMethod.addLocalVariable("start",CtClass.longType)
        ctMethod.insertBefore("start = SystemClock.uptimeMillis();")
        ctMethod.insertAfter("Log.d(\"$ctMethod.name->\", (SystemClock.uptimeMillis() - start)  + \"ms\");")

        ctClass.writeFile(originPath)
        // 释放
        ctClass.detach()
    }
}
