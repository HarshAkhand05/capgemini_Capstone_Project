package com.notes.pages;

import com.notes.utils.WaitUtils;
import org.openqa.selenium.By;
import com.notes.utils.AgenticDriver;

public class AddNotePage extends BasePage {

    private static final By CATEGORY_SELECT = By.id("category");

    private static final By TITLE_FIELD = By.id("title");

    private static final By DESC_FIELD = By.id("description");

    private static final By CREATE_BTN = By.cssSelector("[data-testid='note-submit']");

    private static final By DASHBOARD_ADD_NOTE_BTN = By.cssSelector("[data-testid='add-new-note']");

    private static final By ERROR_MSG = By.cssSelector(".alert.alert-danger");


    public void waitForPageLoad() {

        WaitUtils.waitForPageLoad();

        WaitUtils.waitForVisible(CATEGORY_SELECT);

        WaitUtils.waitForVisible(TITLE_FIELD);

        WaitUtils.waitForVisible(DESC_FIELD);

        WaitUtils.waitForVisible(CREATE_BTN);

        WaitUtils.waitForClickable(CREATE_BTN);
    }

    public AddNotePage selectCategory(String category) {
        selectByVisibleText(CATEGORY_SELECT, category);
        return this;
    }

    public AddNotePage enterTitle(String title) {
        type(TITLE_FIELD, title);
        return this;
    }

    public AddNotePage enterDescription(String desc) {
        type(DESC_FIELD, desc);
        return this;
    }


    public DashboardPage clickCreate() {

//        click(CREATE_BTN);
        AgenticDriver.agentClick(
                By.cssSelector("[data-testid='note-submit']"),  // primary
                By.id("note-submit"),                           // fallback 1
                By.xpath("//button[@type='submit']")            // fallback 2
        );


        WaitUtils.waitForVisible(DASHBOARD_ADD_NOTE_BTN);

        return new DashboardPage();
    }


    public AddNotePage clickCreateExpectingError() {

        click(CREATE_BTN);

        WaitUtils.waitForVisible(ERROR_MSG);

        return this;
    }


    public DashboardPage createNote(
            String category,
            String title,
            String description) {

        log.info(
                "Creating note: category={} title={}",
                category,
                title);

        selectCategory(category);

        enterTitle(title);

        enterDescription(description);

        return clickCreate();
    }


    public boolean isErrorDisplayed() {

        try {

            WaitUtils.waitForVisible(ERROR_MSG);

            return true;

        } catch (Exception e) {

            log.warn(
                    "Note creation error not displayed: {}",
                    e.getMessage());

            return false;
        }
    }

    public String getErrorMessage() {

        WaitUtils.waitForVisible(ERROR_MSG);

        return getText(ERROR_MSG);
    }
}