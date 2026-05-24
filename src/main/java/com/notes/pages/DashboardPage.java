package com.notes.pages;

import com.notes.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

import java.util.List;

public class DashboardPage extends BasePage {

    // Correct Add Note button
    private static final By ADD_NOTE_BTN =
            By.cssSelector("[data-testid='add-new-note']");

    // Correct Profile button
    private static final By PROFILE_ICON =
            By.cssSelector("[data-testid='profile']");

    // Correct Logout button
    private static final By LOGOUT_LINK =
            By.cssSelector("[data-testid='logout']");

    // Note titles
    private static final By NOTE_TITLE_IN_CARD =
            By.cssSelector("[data-testid='note-card-title']");

    // Note cards
    private static final By NOTE_CARDS =
            By.cssSelector(".card");

    /**
     * Dashboard ready validation.
     */
    public boolean isDashboardLoaded() {

        try {

            WaitUtils.waitForVisible(ADD_NOTE_BTN);

            WaitUtils.waitForClickable(ADD_NOTE_BTN);

            return true;

        } catch (Exception e) {

            log.warn(
                    "Dashboard not loaded: {}",
                    e.getMessage());

            return false;
        }
    }

    /**
     * Open Add Note modal.
     */
    public AddNotePage clickAddNote() {

        WaitUtils.waitForVisible(ADD_NOTE_BTN);

        WaitUtils.waitForClickable(ADD_NOTE_BTN);

        click(ADD_NOTE_BTN);

        return new AddNotePage();
    }

    /**
     * Verify note title exists.
     */
    public boolean isNotePresent(String title) {

        List<WebElement> titles =
                driver().findElements(NOTE_TITLE_IN_CARD);

        return titles.stream().anyMatch(element -> {

            try {

                return element
                        .getText()
                        .trim()
                        .equalsIgnoreCase(title);

            } catch (StaleElementReferenceException e) {

                return false;
            }
        });
    }

    /**
     * Current note count.
     */
    public int getNoteCount() {

        try {

            WaitUtils.waitForPageLoad();

            return driver()
                    .findElements(NOTE_CARDS)
                    .size();

        } catch (Exception e) {

            return 0;
        }
    }

    /**
     * Logout current user.
     */
    public void logout() {

        WaitUtils.waitForVisible(LOGOUT_LINK);

        WaitUtils.waitForClickable(LOGOUT_LINK);

        click(LOGOUT_LINK);

        WaitUtils.waitForUrlContains("login");
    }
}