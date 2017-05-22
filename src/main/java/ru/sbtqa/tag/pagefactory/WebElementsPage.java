package ru.sbtqa.tag.pagefactory;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidElementStateException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbtqa.tag.datajack.Stash;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.Page;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.PageContext;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.annotations.ActionTitle;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.annotations.ActionTitles;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.annotations.PageEntry;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.annotations.ValidationRule;
import ru.sbtqa.tag.pagefactory.drivers.TagMobileDriver;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementDescriptionException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementNotFoundException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.FactoryRuntimeException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageInitializationException;
import ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.WaitException;
import ru.sbtqa.tag.pagefactory.extensions.DriverExtension;
import ru.sbtqa.tag.pagefactory.extensions.WebExtension;
import ru.sbtqa.tag.pagefactory.support.AdbConsole;
import ru.sbtqa.tag.pagefactory.support.Environment;
import ru.sbtqa.tag.qautils.errors.AutotestError;
import ru.sbtqa.tag.qautils.reflect.FieldUtilsExt;
import ru.sbtqa.tag.qautils.strategies.MatchStrategy;
import ru.yandex.qatools.htmlelements.element.CheckBox;
import ru.yandex.qatools.htmlelements.element.TypifiedElement;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.sbtqa.tag.pagefactory.ReflectionUtil.*;

/**
 * Contains basic actions in particular with web elements
 * If we want to extend this functional -> inherit from this class
 */
public abstract class WebElementsPage extends Page {

    private static final Logger LOG = LoggerFactory.getLogger(WebElementsPage.class);

    /**
     * Find element with specified title annotation, and fill it with given text
     * Add elementTitle-&gt;text as parameter-&gt;value to corresponding step in
     * allure report
     *
     * @param elementTitle element to fill
     * @param text text to enter
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if page was not
     * initialized, or required element couldn't be found
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.fill.field")
    public void fillField(String elementTitle, String text) throws PageException {
        WebElement webElement = getElementByTitle(elementTitle);
        webElement.click();
        
        if (PageFactory.getEnvironment() == Environment.WEB) {
            webElement.clear();
        }
        
        if (PageFactory.getEnvironment() == Environment.MOBILE && TagMobileDriver.getAppiumClickAdb()) {
            // set ADBKeyBoard as default
            AdbConsole.execute("ime set com.android.adbkeyboard/.AdbIME");
            // send broadcast intent via adb
            AdbConsole.execute(String.format("am broadcast -a ADB_INPUT_TEXT --es msg '%s'", text));
        } else {
            webElement.sendKeys(text);
        }
        
        addToReport(elementTitle, text);
    }

    /**
     * Same as {@link #fillField(String, String)}, but accepts particular
     * WebElement object and interacts with it
     *
     * @param webElement an object to interact with
     * @param text string text to send to element
     */
    public void fillField(WebElement webElement, String text) {
        if (null != text) {
            try {
                webElement.clear();
            } catch (InvalidElementStateException | NullPointerException e) {
                LOG.debug("Failed to clear web element {}", webElement, e);
            }
            webElement.sendKeys(text);
        }

        addToReport(webElement, text);
    }

    /**
     * Click the specified link element
     *
     * @param webElement a WebElement object to click
     */
    public void clickWebElement(WebElement webElement) {
        if (PageFactory.getEnvironment() == Environment.MOBILE && TagMobileDriver.getAppiumClickAdb()) {
            // get center point of element to tap on it
            int x = webElement.getLocation().getX() + webElement.getSize().getWidth() / 2;
            int y = webElement.getLocation().getY() + webElement.getSize().getHeight() / 2;
            AdbConsole.execute(String.format("input tap %s %s", x, y));
        } else {
            webElement.click();
        }
        addToReport(webElement, " is clicked");
    }

    /**
     * Find specified WebElement on a page, and click it Add corresponding
     * parameter to allure report
     *
     * @param elementTitle title of the element to click
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if page was not
     * initialized, or required element couldn't be found
     */
    @ActionTitles({
        @ActionTitle("ru.sbtqa.tag.pagefactory.click.link")
        ,
            @ActionTitle("ru.sbtqa.tag.pagefactory.click.button")})
    public void clickElementByTitle(String elementTitle) throws PageException {
        WebElement webElement;
        try {
            webElement = getElementByTitle(elementTitle);
            DriverExtension.waitForElementGetEnabled(webElement, PageFactory.getTimeOut());
        } catch (NoSuchElementException | WaitException e) {
            LOG.warn("Failed to find element by title {}", elementTitle, e);
            webElement = DriverExtension.waitUntilElementAppearsInDom(By.partialLinkText(elementTitle));
        }
        clickWebElement(webElement);
    }

    /**
     * Press specified key on a keyboard Add corresponding parameter to allure
     * report
     *
     * @param keyName name of the key. See available key names in {@link Keys}
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.press.key")
    public void pressKey(String keyName) {
        Keys key = Keys.valueOf(keyName.toUpperCase());
        Actions actions = PageFactory.getActions();
        actions.sendKeys(key).perform();
        addToReport(keyName, " is pressed");
    }

    /**
     * Focus a WebElement, and send specified key into it Add corresponding
     * parameter to allure report
     *
     * @param keyName name of the key. See available key names in {@link Keys}
     * @param elementTitle title of WebElement that accepts key commands
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementNotFoundException if
     * couldn't find element with required title
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.press.key")
    public void pressKey(String keyName, String elementTitle) throws PageException {
        Keys key = Keys.valueOf(keyName.toUpperCase());
        Actions actions = PageFactory.getActions();
        actions.moveToElement(getElementByTitle(elementTitle));
        actions.click();
        actions.sendKeys(key);
        actions.build().perform();
        addToReport(keyName, " is pressed on element " + elementTitle + "'");
    }

    /**
     * Send key to element and add corresponding parameter to allure report
     *
     * @param webElement WebElement to send keys in
     * @param keyName name of the key. See available key names in {@link Keys}
     */
    public void pressKey(WebElement webElement, Keys keyName) {
        webElement.sendKeys(keyName);
        addToReport(webElement, " is pressed by key '" + keyName + "'");
    }

    /**
     * Find web element with corresponding title, if it is a check box, select
     * it If it's a WebElement instance, check whether it is already selected,
     * and click if it's not Add corresponding parameter to allure report
     *
     * @param elementTitle WebElement that is supposed to represent checkbox
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if page was not
     * initialized, or required element couldn't be found
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.select.checkBox")
    public void setCheckBox(String elementTitle) throws PageException {
        WebElement targetElement = getElementByTitle(elementTitle);
        if (targetElement.getClass().isAssignableFrom(CheckBox.class)) {
            ((CheckBox) targetElement).select();
        } else {
            setCheckBoxState(targetElement, true);
        }
        addToReport(elementTitle, " is checked");
    }

    /**
     * Check whether specified element is selected, if it isn't, click it
     * isSelected() doesn't guarantee correct behavior if given element is not a
     * selectable (checkbox,dropdown,radiobtn)
     *
     * @param webElement a WebElemet object.
     * @param state a boolean object.
     */
    public void setCheckBoxState(WebElement webElement, Boolean state) {
        if (null != state) {
            if (webElement.isSelected() != state) {
                webElement.click();
            }
        }
        addToReport(webElement, " is turned to '" + state + "' state");
    }

    /**
     * Find element with required title, perform
     * {@link #select(WebElement, String, MatchStrategy)} on found element Use
     * exact match strategy
     *
     * @param elementTitle WebElement that is supposed to be selectable
     * @param option option to select
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if required
     * element couldn't be found, or current page isn't initialized
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.select")
    public void select(String elementTitle, String option) throws PageException {
        WebElement webElement = getElementByTitle(elementTitle);
        if (null != option) {
            select(webElement, option, MatchStrategy.EXACT);
        }
    }

    /**
     * Find element with required title, perform
     * {@link #select(WebElement, String, MatchStrategy)} on found element Use
     * given match strategy
     *
     * @param elementTitle the title of SELECT element to interact
     * @param option the value to match against
     * @param strategy the strategy to match value
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if required
     * element couldn't be found, or current page isn't initialized
     */
    public void select(String elementTitle, String option, MatchStrategy strategy) throws PageException {
        WebElement webElement = getElementByTitle(elementTitle);
        select(webElement, option, strategy);
    }

    /**
     * Try to extract selectable options form given WebElement, and select
     * required one Add corresponding parameter to allure report
     *
     * @param webElement WebElement for interaction. Element is supposed to be
     * selectable, i.e. have select options
     * @param option the value to match against
     * @param strategy the strategy to match value. See {@link MatchStrategy}
     * for available values
     */
    @SuppressWarnings("unchecked")
    public void select(WebElement webElement, String option, MatchStrategy strategy) {
        String jsString = ""
                + "var content=[]; "
                + "var options = arguments[0].getElementsByTagName('option'); "
                + " for (var i=0; i<options.length;i++){"
                + " content.push(options[i].text)"
                + "}"
                + "return content";
        List<String> options = (ArrayList<String>) ((JavascriptExecutor) PageFactory.getDriver()).
                executeScript(jsString, webElement);

        boolean isSelectionMade = false;
        for (int index = 0; index < options.size(); index++) {
            boolean isCurrentOption = false;
            String optionText = options.get(index).replaceAll("\\s+", "");
            String needOptionText = option.replaceAll("\\s+", "");

            if (strategy.equals(MatchStrategy.CONTAINS)) {
                isCurrentOption = optionText.contains(needOptionText);
            } else if (strategy.equals(MatchStrategy.EXACT)) {
                isCurrentOption = optionText.equals(needOptionText);
            }

            if (isCurrentOption) {
                Select select = new Select(webElement);
                select.selectByIndex(index);
                isSelectionMade = true;
                break;
            }
        }

        if (!isSelectionMade) {
            throw new AutotestError("There is no element '" + option + "' in " + getElementTitle(webElement));
        }

        addToReport(webElement, option);
    }

    /**
     * Wait for an alert with specified text, and accept it
     *
     * @param text alert message
     * @throws WaitException in case if alert didn't appear during default wait
     * timeout
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.accept.alert")
    public void acceptAlert(String text) throws WaitException {
        DriverExtension.interactWithAlert(text, true);
    }

    /**
     * Wait for an alert with specified text, and dismiss it
     *
     * @param text alert message
     * @throws WaitException in case if alert didn't appear during default wait
     * timeout
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.dismiss.alert")
    public void dismissAlert(String text) throws WaitException {
        DriverExtension.interactWithAlert(text, false);
    }

    /**
     * Wait for appearance of the required text in current DOM model. Text will
     * be space-trimmed, so only non-space characters will matter.
     *
     * @param text text to search
     * @throws WaitException if text didn't appear on the page during the
     * timeout
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.text.appears.on.page")
    public void assertTextAppears(String text) throws WaitException {
        WebExtension.waitForTextPresenceInPageSource(text, true);
    }

    /**
     * Check whether specified text is absent on the page. Text is being
     * space-trimmed before assertion, so only non-space characters will matter
     *
     * @param text text to search for
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.text.absent.on.page")
    public void assertTextIsNotPresent(String text) {
        WebExtension.waitForTextPresenceInPageSource(text, false);
    }

    /**
     * Wait for a new browser window, then wait for a specific text inside the
     * appeared window List of previously opened windows is being saved before
     * each click, so if modal window appears without click, this method won't
     * catch it. Text is being waited by {@link #assertTextAppears}, so it will
     * be space-trimmed as well
     *
     * @param text text that will be searched inside of the window
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.WaitException if
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.modal.window.with.text.appears")
    public void assertModalWindowAppears(String text) throws WaitException {
        try {
            String popupHandle = WebExtension.findNewWindowHandle((Set<String>) Stash.getValue("beforeClickHandles"));
            if (null != popupHandle && !popupHandle.isEmpty()) {
                PageFactory.getWebDriver().switchTo().window(popupHandle);
            }
            assertTextAppears(text);
        } catch (Exception ex) {
            throw new WaitException("Modal window with text '" + text + "' didn't appear during timeout", ex);
        }
    }

    /**
     * Perform {@link #checkValue(String, WebElement, MatchStrategy)} for the
     * WebElement with corresponding title on a current page. Use exact match
     * strategy
     *
     * @param text string value that will be searched inside of the element
     * @param elementTitle title of the element to search
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementNotFoundException if
     * couldn't find element by given title, or current page isn't initialized
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.check.value")
    public void checkValue(String elementTitle, String text) throws PageException {
        checkValue(text, getElementByTitle(elementTitle), MatchStrategy.EXACT);
    }

    /**
     * Perform {@link #checkValue(String, WebElement, MatchStrategy)} for the
     * specified WebElement. Use exact match strategy
     *
     * @param text string value that will be searched inside of the element
     * @param webElement WebElement to check
     */
    public void checkValue(String text, WebElement webElement) {
        checkValue(text, webElement, MatchStrategy.EXACT);
    }

    /**
     * Define a type of given WebElement, and check whether it either contains,
     * or exactly matches given text in its value. Currently supported elements
     * are text input and select box TODO: use HtmlElements here, to define
     * which element we are dealing with
     *
     * @param text string value that will be searched inside of the element
     * @param webElement WebElement to check
     * @param searchStrategy match strategy. See available strategies in
     * {@link MatchStrategy}
     */
    public void checkValue(String text, WebElement webElement, MatchStrategy searchStrategy) {
        String value = "";
        switch (searchStrategy) {
            case EXACT:
                try {
                    switch (webElement.getTagName()) {
                        case "input":
                            value = webElement.getAttribute("value");
                            Assert.assertEquals(text.replaceAll("\\s+", ""), value.replaceAll("\\s+", ""));
                            break;
                        case "select":
                            value = webElement.getAttribute("title");
                            if (value.isEmpty() || !value.replaceAll("\\s+", "").equals(text.replaceAll("\\s+", ""))) {
                                value = webElement.getText();
                            }
                            Assert.assertEquals(text.replaceAll("\\s+", ""), value.replaceAll("\\s+", ""));
                            break;
                        default:
                            value = webElement.getText();
                            Assert.assertEquals(text.replaceAll("\\s+", ""), value.replaceAll("\\s+", ""));
                            break;
                    }
                } catch (Exception | AssertionError exception) {
                    throw new AutotestError("The actual value '" + value + "' of WebElement '" + webElement + "' are not equal to expected text '" + text + "'", exception);
                }
                break;
            case CONTAINS:
                try {
                    switch (webElement.getTagName()) {
                        case "input":
                            value = webElement.getAttribute("value");
                            Assert.assertTrue(value.replaceAll("\\s+", "").contains(text.replaceAll("\\s+", "")));
                            break;
                        case "select":
                            value = webElement.getAttribute("title");
                            if (value.isEmpty() || !value.replaceAll("\\s+", "").contains(text.replaceAll("\\s+", ""))) {
                                value = webElement.getText();
                            }
                            Assert.assertTrue(value.replaceAll("\\s+", "").contains(text.replaceAll("\\s+", "")));
                            break;
                        default:
                            value = webElement.getText();
                            Assert.assertTrue(value.replaceAll("\\s+", "").contains(text.replaceAll("\\s+", "")));
                            break;
                    }
                } catch (Exception | AssertionError exception) {
                    throw new AutotestError("The actual value '" + value + "' of WebElement '" + webElement + "' are not equal to expected text '" + text + "'", exception);
                }
                break;
        }

    }

    /**
     * Find element by given title, and check whether it is not empty See
     * {@link #checkFieldIsNotEmpty(WebElement)} for details
     *
     * @param elementTitle title of the element to check
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if current page
     * was not initialized, or element wasn't found on the page
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.check.field.not.empty")
    public void checkFieldIsNotEmpty(String elementTitle) throws PageException {
        WebElement webElement = getElementByTitle(elementTitle);
        checkFieldIsNotEmpty(webElement);
    }

    /**
     * Check that given WebElement has a value attribute, and it is not empty
     *
     * @param webElement WebElement to check
     */
    public void checkFieldIsNotEmpty(WebElement webElement) {
        String value = webElement.getText();
        if (value.isEmpty()) {
            value = webElement.getAttribute("value");
        }
        try {
            Assert.assertFalse(value.replaceAll("\\s+", "").isEmpty());
        } catch (Exception | AssertionError e) {
            throw new AutotestError("The field" + getElementTitle(webElement) + " is empty", e);
        }
    }

    /**
     * Find element with corresponding title, and make sure that its value is
     * not equal to given text Text, as well as element value are being
     * space-trimmed before comparison, so only non-space characters matter
     *
     * @param text element value for comparison
     * @param elementTitle title of the element to search
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if current page
     * wasn't initialized, or element with required title was not found
     */
    @ActionTitle("ru.sbtqa.tag.pagefactory.check.values.not.equal")
    public void checkValuesAreNotEqual(String text, String elementTitle) throws PageException {
        WebElement webElement = this.getElementByTitle(elementTitle);
        if (checkValuesAreNotEqual(text, webElement)) {
            throw new AutotestError("'" + text + "' is equal with '" + elementTitle + "' value");
        }
    }

    /**
     * Extract value from the given WebElement, and compare the it with the
     * given text Text, as well as element value are being space-trimmed before
     * comparison, so only non-space characters matter
     *
     * @param text a {@link java.lang.String} object.
     * @param webElement a {@link org.openqa.selenium.WebElement} object.
     * @return a boolean.
     */
    public boolean checkValuesAreNotEqual(String text, WebElement webElement) {
        if ("input".equals(webElement.getTagName())) {
            return webElement.getAttribute("value").replaceAll("\\s+", "").equals(text.replaceAll("\\s+", ""));
        } else {
            return webElement.getText().replaceAll("\\s+", "").equals(text.replaceAll("\\s+", ""));
        }
    }

    /**
     * Perform a check that there is an element with required text on current
     * page
     *
     * @param text a {@link java.lang.String} object.
     */
    @ActionTitles({
        @ActionTitle("ru.sbtqa.tag.pagefactory.check.element.with.text.present")
        ,
            @ActionTitle("ru.sbtqa.tag.pagefactory.check.text.visible")})
    public void checkElementWithTextIsPresent(String text) {
        if (!DriverExtension.checkElementWithTextIsPresent(text, PageFactory.getTimeOutInSeconds())) {
            throw new AutotestError("Text '" + text + "' is not present");
        }
    }




    /**
     * Get title of current page obect
     *
     * @return the title
     */
    public String getPageTitle() {
        return this.getClass().getAnnotation(PageEntry.class).title();
    }

    /**
     * Search for the given WebElement in page repository storage, that is being
     * generated during preconditions to all tests. If element is found, return
     * its title annotation. If nothing found, log debug message and return
     * toString() of corresponding element
     *
     * @param element WebElement to search
     * @return title of the given element
     */
    public String getElementTitle(WebElement element) {
        for (Map.Entry<Field, String> entry : PageFactory.getPageRepository().get(this.getClass()).entrySet()) {
            try {
                if (getElementByField(this, entry.getKey()) == element) {
                    return entry.getValue();
                }
            } catch (NoSuchElementException | StaleElementReferenceException | ElementDescriptionException ex) {
                LOG.debug("Failed to get element '" + element + "' title", ex);
            }
        }
        return element.toString();
    }

    /**
     * Return class for redirect if annotation contains and null if not present
     *
     * @param element element, redirect for which is being searched
     * @return class of the page object, element redirects to
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.ElementDescriptionException
     * if failed to find redirect
     */
    public Class<? extends WebElementsPage> getElementRedirect(WebElement element) throws ElementDescriptionException {
        try {
            Page currentPage = PageContext.getCurrentPage();
            if (null == currentPage) {
                LOG.warn("Current page not initialized yet. You must initialize it by hands at first time only.");
                return null;
            }
            return findRedirect(currentPage, element);
        } catch (IllegalArgumentException | PageInitializationException ex) {
            throw new ElementDescriptionException("Failed to get element redirect", ex);
        }
    }

    /**
     * Find specified WebElement by title annotation among current page fields
     *
     * @param title title of the element to search
     * @return WebElement found by corresponding title
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if failed to
     * find corresponding element or element type is set incorrectly
     */
    public WebElement getElementByTitle(String title) throws PageException {
        for (Field field : FieldUtilsExt.getDeclaredFieldsWithInheritance(this.getClass())) {
            if (isRequiredElement(field, title)) {
                return getElementByField(this, field);
            }
        }

        throw new ElementNotFoundException(String.format("Element '%s' is not present on current page '%s''", title, this.getPageTitle()));
    }

    /**
     * Find a method with {@link ValidationRule} annotation on current page, and
     * call it
     *
     * @param title title of the validation rule
     * @param params parameters passed to called method
     * @throws ru.sbtqa.tag.pagefactory.maven_artefacts.module_pagefactory_api.exceptions.PageException if couldn't
     * find corresponding validation rule
     */
    public void fireValidationRule(String title, Object... params) throws PageException {
        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            if (null != method.getAnnotation(ValidationRule.class)
                    && method.getAnnotation(ValidationRule.class).title().equals(title)) {
                try {
                    method.invoke(this, params);
                } catch (InvocationTargetException | IllegalArgumentException | IllegalAccessException e) {
                    LOG.debug("Failed to invoke method {}", method, e);
                    throw new FactoryRuntimeException("Failed to invoke method", e);
                }
                return;
            }
        }
        throw new PageException("There is no '" + title + "' validation rule in '" + this.getPageTitle() + "' page.");
    }
}
