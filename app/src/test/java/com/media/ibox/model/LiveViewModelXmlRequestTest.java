package com.media.ibox.model;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveViewModelXmlRequestTest {

    @Test
    public void sourceChangeInvalidatesAnAlreadyQueuedXmlResult() {
        LiveViewModel viewModel = new LiveViewModel();
        LiveViewModel.XmlResult old = new LiveViewModel.XmlResult(0, true);
        assertTrue(viewModel.isCurrentXmlResult(old));

        viewModel.invalidateXml();

        assertFalse(viewModel.isCurrentXmlResult(old));
    }
}
