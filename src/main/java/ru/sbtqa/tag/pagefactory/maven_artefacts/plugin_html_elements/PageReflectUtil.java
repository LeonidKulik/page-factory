package ru.sbtqa.tag.pagefactory.maven_artefacts.plugin_html_elements;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.Page;
import ru.sbtqa.tag.pagefactory.WebElementsPage;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.annotations.ElementTitle;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementDescriptionException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementNotFoundException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.FactoryRuntimeException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException;
import ru.sbtqa.tag.qautils.reflect.FieldUtilsExt;
import ru.yandex.qatools.htmlelements.element.HtmlElement;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Static methods for finding block and execution methods by ActionTitles
 * And some methods for recursive search
 */
public class PageReflectUtil {
    
    /**
     * Find element with required title and type inside of the given block.
     * Return null if didn't find any
     *
     * @param block block object
     * @param elementTitle value of ElementTitle annotation of required
     * element
     * @param type type of element to return
     * @param <T> any WebElement or its derivative
     * @return found element or null (exception should be thrown by a caller
     * that could no find any elements)
     */
    private static <T extends WebElement> T findElementInBlock(Page page, HtmlElement block, String elementTitle, Class<T> type)
            throws ElementDescriptionException {
        for (Field f : FieldUtils.getAllFields(block.getClass())) {
            if (isRequiredElement(f, elementTitle) && f.getType().equals(type)) {
                f.setAccessible(true);
                try {
                    return type.cast(f.get(block));
                } catch (IllegalAccessException iae) {
                    // Since we explicitly set the field to be accessible, this exception is unexpected.
                    // It might mean that we try to get field in context of an object it doesn't belong to
                    throw new FactoryRuntimeException(
                            String.format("Internal error during attempt to find element '%s' in block '%s'",
                                    elementTitle, block.getName()), iae);
                } catch (ClassCastException cce) {
                    throw new ElementDescriptionException(
                            String.format("Element '%s' was found in block '%s', but it's type is incorrect."
                                            + "Requested '%s', but got '%s'",
                                    elementTitle, block.getName(), type.getName(), f.getType()), cce);
                }
            }
        }
        return null;
    }
    
    /**
     * Check if corresponding field is a block. I.e. it has
     * {@link ElementTitle} annotation and extends {@link HtmlElement} class
     * directly
     *
     * @param field field that is being checked
     * @return true|false
     */
    private static boolean isBlockElement(Field field) {
        
        return (null != field.getAnnotation(ElementTitle.class))
                && isChildOf(HtmlElement.class, field);
    }
    
    
    /**
     * Finds blocks by required path/name in the given context. Block is a
     * class that extends HtmlElement. If blockPath contains delimiters, it
     * will be treated as a full path, and block should be located by the
     * exactly that path. Otherwise, recursive search via all blocks is
     * being performed
     *
     * @param blockPath full path or just a name of the block to search
     * @param context object where the search will be performed
     * @param returnFirstFound whether the search should be stopped on a
     * first found block (for faster searches)
     * @return list of found blocks. could be empty
     * @throws IllegalAccessException if called with invalid context
     */
    private static List<HtmlElement> findBlocks(Page page, String blockPath, Object context, boolean returnFirstFound)
            throws IllegalAccessException {
        String[] blockChain;
        if (blockPath.contains("->")) {
            blockChain = blockPath.split("->");
        } else {
            blockChain = new String[]{blockPath};
        }
        List<HtmlElement> found = new ArrayList<>();
        for (Field currentField : FieldUtilsExt.getDeclaredFieldsWithInheritance(context.getClass())) {
            if (isBlockElement(currentField)) {
                if (isRequiredElement(currentField, blockChain[0])) {
                    currentField.setAccessible(true);
                    // isBlockElement() ensures that this is a HtmlElement instance
                    HtmlElement foundBlock = (HtmlElement) currentField.get(context);
                    if (blockChain.length == 1) {
                        // Found required block directly inside the context
                        found.add(foundBlock);
                        if (returnFirstFound) {
                            return found;
                        }
                    } else {
                        // Continue to search in the element chain, reducing its length by the first found element
                        // +2 because '->' adds 2 symbols
                        String reducedPath = blockPath.substring(blockChain[0].length() + 2);
                        found.addAll(findBlocks(reducedPath, foundBlock, returnFirstFound));
                    }
                } else if (blockChain.length == 1) {
                    found.addAll(findBlocks(blockPath, currentField.get(context), returnFirstFound));
                }
            }
        }
        return found;
    }
    
    /**
     * Find element of required type in the specified block, or block chain on
     * the page. If blockPath is separated by &gt; delimiters, it will be
     * treated as a block path, so element could be found only in the described
     * chain of blocks. Otherwise, given block will be searched recursively on
     * the page
     *
     * @param blockPath block or block chain where element will be searched
     * @param title value of ElementTitle annotation of required element
     * @param type type of the searched element
     * @return web element of the required type
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementNotFoundException if
     * element was not found
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementDescriptionException
     * if element was not found, but with the wrong type
     */
    public static  <T extends WebElement> T findElementInBlockByTitle(Page page, String blockPath, String title, Class<T> type)
            throws PageException {
        for (HtmlElement block : findBlocks(blockPath)) {
            T found = WebElementsPage.Core.findElementInBlock(block, title, type);
            if (null != found) {
                return found;
            }
        }
        throw new ElementNotFoundException(String.format("Couldn't find element '%s' in '%s'", title, blockPath));
    }
    
    /**
     * Acts exactly like
     * {@link #findElementInBlockByTitle(String, String, Class)}, but return a
     * WebElement instance. It still could be casted to HtmlElement and
     * TypifiedElement any class that extend them.
     *
     * @param blockPath block or block chain where element will be searched
     * @param title value of ElementTitle annotation of required element
     * @return WebElement
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementNotFoundException if
     * element was not found
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementDescriptionException
     * if element was not found, but with the wrong type
     */
    public static WebElement findElementInBlockByTitle(Page page, String blockPath, String title) throws PageException {
        return findElementInBlockByTitle(blockPath, title, WebElement.class);
    }
    
    /**
     * Finds elements list in context of current page See
     * ${@link WebElementsPage.Core#findListOfElements(String, Class, Object)} for detailed
     * description
     *
     * @param listTitle value of ElementTitle annotation of required element
     * @param type type of elements in list that is being searched for
     * @param <T> type of elements in returned list
     * @return list of elements of particular type
     * @throws PageException if nothing found or current page is not initialized
     */
    public static  <T extends WebElement> List<T> findListOfElements(Page page, String listTitle, Class<T> type)
            throws PageException {
        return WebElementsPage.Core.findListOfElements(listTitle, type, this);
    }
    
    /**
     * Find elements list in context of required block See
     * ${@link WebElementsPage.Core#findListOfElements(String, Class, Object)} for detailed
     * description
     *
     * @param blockPath full path or just a name of the block to search
     * @param listTitle value of ElementTitle annotation of required element
     * @param type type of elements in list that is being searched for
     * @param <T> type of elements in returned list
     * @return list of elements of particular type
     * @throws PageException if nothing found or current page is not initialized
     */
    public static  <T extends WebElement> List<T> findListOfElementsInBlock(Page page, String blockPath, String listTitle, Class<T> type)
            throws PageException {
        Object block = findBlock(blockPath);
        return WebElementsPage.Core.findListOfElements(listTitle, type, block);
    }
    
    /**
     * Finds elements list in context of required block See
     * ${@link WebElementsPage.Core#findListOfElements(String, Class, Object)} for detailed
     * description
     *
     * @param blockPath full path or just a name of the block to search
     * @param listTitle value of ElementTitle annotation of required element
     * @return list of WebElement's
     * @throws PageException if nothing found or current page is not initialized
     */
    public static List<WebElement> findListOfElementsInBlock(Page page, String blockPath, String listTitle) throws PageException {
        return findListOfElementsInBlock(blockPath, listTitle, WebElement.class);
    }
    
    /**
     * See {@link WebElementsPage.Core#findBlocks(String, Object, boolean)} for description.
     * This wrapper finds only one block. Search is being performed on a current
     * page
     *
     * @param blockPath full path or just a name of the block to search
     * @return first found block object
     * @throws java.util.NoSuchElementException if couldn't find any block
     */
    public static HtmlElement findBlock(Page page, String blockPath) throws NoSuchElementException {
        try {
            List<HtmlElement> blocks = WebElementsPage.Core.findBlocks(blockPath, this, true);
            if (blocks.isEmpty()) {
                throw new java.util.NoSuchElementException(String.format("Couldn't find block '%s' on a page '%s'",
                        blockPath, this.getPageTitle()));
            }
            return blocks.get(0);
        } catch (IllegalAccessException ex) {
            throw new FactoryRuntimeException(String.format("Internal error during attempt to find block '%s'", blockPath), ex);
        }
    }
    
    /**
     * See {@link WebElementsPage.Core#findBlocks(String, Object, boolean)} for description.
     * Search is being performed on a current page
     *
     * @param blockPath full path or just a name of the block to search
     * @return list of objects that were found by specified path
     */
    public static List<HtmlElement> findBlocks(Page page, String blockPath) throws NoSuchElementException {
        try {
            return WebElementsPage.Core.findBlocks(blockPath, this, false);
        } catch (IllegalAccessException ex) {
            throw new FactoryRuntimeException(String.format("Internal error during attempt to find a block '%s'", blockPath), ex);
        }
    }
    
    /**
     * Execute parameter-less method inside of the given block element.
     *
     * @param block block title, or a block chain string separated with '-&gt;'
     * symbols
     * @param actionTitle title of the action to execute
     * @throws java.lang.NoSuchMethodException if required method couldn't be
     * found
     */
    public static void executeMethodByTitleInBlock(Page page, String block, String actionTitle) throws NoSuchMethodException {
        executeMethodByTitleInBlock(block, actionTitle, new Object[0]);
    }
    
    /**
     * Execute method with one or more parameters inside of the given block
     * element !BEWARE! If there are several elements found by specified block
     * path, a first one will be used!
     *
     * @param blockPath block title, or a block chain string separated with
     * '-&gt;' symbols
     * @param actionTitle title of the action to execute
     * @param parameters parameters that will be passed to method
     * @throws java.lang.NoSuchMethodException if required method couldn't be
     * found
     */
    public static void executeMethodByTitleInBlock(Page page, String blockPath, String actionTitle, Object... parameters) throws NoSuchMethodException {
        HtmlElement block = findBlock(blockPath);
        Method[] methods = block.getClass().getMethods();
        for (Method method : methods) {
            if (WebElementsPage.Core.isRequiredAction(method, actionTitle)) {
                try {
                    method.setAccessible(true);
                    if (parameters == null || parameters.length == 0) {
                        MethodUtils.invokeMethod(block, method.getName());
                    } else {
                        MethodUtils.invokeMethod(block, method.getName(), parameters);
                    }
                    break;
                } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                    throw new FactoryRuntimeException(String.format("Failed to execute method '%s' in the following block: '%s'",
                            actionTitle, blockPath), e);
                }
            }
        }
        
        throw new NoSuchMethodException(String.format("There is no '%s' method in block '%s'", actionTitle, blockPath));
    }
    
}
