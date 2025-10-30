package com.theboringdevelopers.lenter.feature.resources

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import javax.swing.Icon

class ComposeResourceDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        element: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        val nameRef = element?.getNonStrictParentOfType<KtNameReferenceExpression>() ?: return null
        val resourceName = nameRef.getReferencedName()
        if (resourceName.isEmpty()) return null

        val qualified = nameRef.getQualifiedExpressionForSelector() ?: return null
        val receiverExpr = qualified.receiverExpression

        when (receiverExpr.resAccessSegmentOrNull()) {
            STRING_SEGMENT -> {
                val targets = findStringResourceTags(element.project, resourceName)
                if (targets.isEmpty()) return null
                return targets.map { LocalizedStringNavigationItem(it.tag, it.localeLabel) }.toTypedArray()
            }

            DRAWABLE_SEGMENT -> {
                val targets = findDrawableResourceFiles(element.project, resourceName)
                if (targets.isEmpty()) return null
                return targets.map { DrawableNavigationItem(it.file, it.localeLabel) }.toTypedArray()
            }

            else -> return null
        }
    }

    override fun getActionText(context: DataContext): String? = null

    private fun findStringResourceTags(project: Project, resourceName: String): List<LocalizedStringTarget> {
        val scope = GlobalSearchScope.projectScope(project)
        val matchingVFiles = FilenameIndex
            .getVirtualFilesByName(project, STRINGS_XML, scope)
            .asSequence()
            .filter { it.isComposeStringsFile() }
            .toList()

        if (matchingVFiles.isEmpty()) return emptyList()

        val psi = PsiManager.getInstance(project)
        val out = ArrayList<LocalizedStringTarget>(matchingVFiles.size)

        for (vf in matchingVFiles) {
            val xmlFile = psi.findFile(vf) as? XmlFile ?: continue
            val root = xmlFile.rootTag ?: continue
            root.findSubTags(STRING_TAG).forEach { tag ->
                if (tag.getAttributeValue(NAME_ATTRIBUTE) == resourceName) {
                    out += LocalizedStringTarget(tag, vf.localeLabel())
                }
            }
        }
        return out
    }

    private fun VirtualFile.isComposeStringsFile(): Boolean {
        val normalized = path.replace('\\', '/')
        return name == STRINGS_XML && normalized.contains("/$COMPOSE_RESOURCES_DIR/")
    }

    private fun findDrawableResourceFiles(project: Project, resourceName: String): List<DrawableTarget> {
        val scope = GlobalSearchScope.projectScope(project)
        val psi = PsiManager.getInstance(project)

        val results = ArrayList<DrawableTarget>()
        for (ext in DRAWABLE_FILE_EXTS) {
            val fileName = "$resourceName.$ext"
            val vFiles = FilenameIndex.getVirtualFilesByName(project, fileName, scope)
                .asSequence()
                .filter { it.isComposeDrawableFile() }
                .toList()

            for (vf in vFiles) {
                val psiFile = psi.findFile(vf) ?: continue
                results += DrawableTarget(psiFile, vf.localeLabel())
            }
        }
        return results
    }

    private fun VirtualFile.isComposeDrawableFile(): Boolean {
        val normalized = path.replace('\\', '/')
        if (!normalized.contains("/$COMPOSE_RESOURCES_DIR/")) return false
        var cur: VirtualFile? = parent
        while (cur != null && cur.name != COMPOSE_RESOURCES_DIR) {
            if (cur.name.startsWith(DRAWABLE_DIR_PREFIX)) return true
            cur = cur.parent
        }
        return false
    }

    private fun VirtualFile.localeLabel(): String {
        val composeResDir = generateSequence(parent) { it.parent }
            .firstOrNull { it.name == COMPOSE_RESOURCES_DIR }

        val qualifiers = mutableListOf<String>()
        var cur: VirtualFile? = parent
        while (cur != null && cur != composeResDir) {
            qualifiers += cur.name
            cur = cur.parent
        }
        qualifiers.reverse()

        val qualifierPart = qualifiers.joinToString("/")
        val sourceSet = composeResDir?.parent?.name

        return buildString {
            if (!sourceSet.isNullOrEmpty() && sourceSet != SRC_DIRECTORY_NAME) append(sourceSet)
            if (qualifierPart.isNotEmpty()) {
                if (isNotEmpty()) append(LOCALE_SEPARATOR)
                append(qualifierPart)
            }
            if (isEmpty()) append(DEFAULT_LOCALE_LABEL)
        }
    }

    /**
     * Возвращает сегмент доступа `Res.<segment>` (например, "string" или "drawable"), если это именно доступ через Res.
     */
    private fun KtExpression.resAccessSegmentOrNull(): String? {
        val dot = this as? KtDotQualifiedExpression ?: return null
        val selector = dot.selectorExpression as? KtNameReferenceExpression ?: return null
        val recv = dot.receiverExpression as? KtNameReferenceExpression ?: return null
        if (recv.getReferencedName() != RES_OBJECT_NAME) return null
        return selector.getReferencedName()
    }

    private data class LocalizedStringTarget(
        val tag: XmlTag,
        val localeLabel: String,
    )

    private data class DrawableTarget(
        val file: PsiFile,
        val localeLabel: String,
    )

    private class LocalizedStringNavigationItem(
        private val tag: XmlTag,
        private val localeLabel: String,
    ) : FakePsiElement(), NavigationItem {

        override fun getProject() = tag.project
        override fun getParent(): PsiElement? = tag.parent
        override fun getContainingFile(): PsiFile = tag.containingFile
        override fun getName(): String? = tag.getAttributeValue(NAME_ATTRIBUTE)
        override fun getNavigationElement(): PsiElement = tag.navigationElement

        private fun asNavigatable(): Navigatable? {
            val navEl = tag.navigationElement
            return (navEl as? Navigatable) ?: (tag as? Navigatable)
        }

        override fun navigate(requestFocus: Boolean) {
            asNavigatable()?.navigate(requestFocus)
        }

        override fun canNavigate(): Boolean = asNavigatable()?.canNavigate() == true

        override fun canNavigateToSource(): Boolean = asNavigatable()?.canNavigateToSource() == true

        override fun getPresentation(): ItemPresentation = object : ItemPresentation {
            override fun getPresentableText(): String {
                val fileName = tag.containingFile.name
                return "$fileName — $localeLabel"
            }

            override fun getLocationString(): String? =
                tag.containingFile.virtualFile?.parent?.path

            override fun getIcon(unused: Boolean): Icon? = tag.getIcon(0)
        }
    }

    private class DrawableNavigationItem(
        private val file: PsiFile,
        private val localeLabel: String,
    ) : FakePsiElement(), NavigationItem {

        override fun getProject() = file.project
        override fun getParent(): PsiElement? = file.parent
        override fun getContainingFile(): PsiFile = file
        override fun getName(): String? = file.virtualFile?.nameWithoutExtension
        override fun getNavigationElement(): PsiElement = file.navigationElement

        private fun asNavigatable(): Navigatable? {
            val navEl = file.navigationElement
            return (navEl as? Navigatable) ?: (file as? Navigatable)
        }

        override fun navigate(requestFocus: Boolean) {
            asNavigatable()?.navigate(requestFocus)
        }

        override fun canNavigate(): Boolean = asNavigatable()?.canNavigate() == true

        override fun canNavigateToSource(): Boolean = asNavigatable()?.canNavigateToSource() == true

        override fun getPresentation(): ItemPresentation = object : ItemPresentation {
            override fun getPresentableText(): String {
                val fileName = file.name
                return "$fileName — $localeLabel"
            }

            override fun getLocationString(): String? = file.virtualFile?.parent?.path

            override fun getIcon(unused: Boolean): Icon? = file.getIcon(0)
        }
    }

    companion object {
        private const val STRINGS_XML = "strings.xml"
        private const val STRING_TAG = "string"
        private const val NAME_ATTRIBUTE = "name"

        private const val RES_OBJECT_NAME = "Res"
        private const val STRING_SEGMENT = "string"
        private const val DRAWABLE_SEGMENT = "drawable"

        private const val COMPOSE_RESOURCES_DIR = "composeResources"
        private const val SRC_DIRECTORY_NAME = "src"
        private const val DEFAULT_LOCALE_LABEL = "default"
        private const val LOCALE_SEPARATOR = " · "
        private const val DRAWABLE_DIR_PREFIX = "drawable"

        private val DRAWABLE_FILE_EXTS = listOf(
            "png", "jpg", "jpeg", "webp", "svg", "xml", "avif", "gif"
        )
    }
}