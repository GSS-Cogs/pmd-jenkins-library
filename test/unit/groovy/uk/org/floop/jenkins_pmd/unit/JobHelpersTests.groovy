package uk.org.floop.jenkins_pmd.unit

import org.junit.Test
import uk.org.floop.jenkins_pmd.helpers.JobHelpers

class JobHelpersTests {
    @Test
    void "ensureInfoJsonFileCorrectlySetHttpsUrl"() {
        def (label, url) = JobHelpers.getMaybeInfoJsonLabelAndUrl(
                "https://github.com/GSS-Cogs/family-covid-19",
                "18ea9cb5a21a9e9f22af164ee1ab252083a2e845",
                "datasets/BEIS-Weekly-road-fuel-prices"
        );

        assert label == "info.json inside family-covid-19/datasets/BEIS-Weekly-road-fuel-prices"
        assert url == "https://github.com/GSS-Cogs/family-covid-19/tree/18ea9cb5a21a9e9f22af164ee1ab252083a2e845/datasets/BEIS-Weekly-road-fuel-prices/info.json"
    }

    @Test
    void "ensureInfoJsonFileCorrectlySetSSHUrl"() {
        def (label, url) = JobHelpers.getMaybeInfoJsonLabelAndUrl(
                "git@github.com:GSS-Cogs/family-covid-19.git",
                "18ea9cb5a21a9e9f22af164ee1ab252083a2e845",
                "datasets/BEIS-Weekly-road-fuel-prices"
        );

        assert label == "info.json inside family-covid-19/datasets/BEIS-Weekly-road-fuel-prices"
        assert url == "https://github.com/GSS-Cogs/family-covid-19/tree/18ea9cb5a21a9e9f22af164ee1ab252083a2e845/datasets/BEIS-Weekly-road-fuel-prices/info.json"
    }


    @Test
    void "ensureInfoJsonFileHandlesNulls"() {
        def (label, url) = JobHelpers.getMaybeInfoJsonLabelAndUrl(
                null,
                "18ea9cb5a21a9e9f22af164ee1ab252083a2e845",
                "datasets/BEIS-Weekly-road-fuel-prices"
        )
        assert label == null
        assert url == null

        def (label2, url2) = JobHelpers.getMaybeInfoJsonLabelAndUrl(
                "git@github.com:GSS-Cogs/family-covid-19.git",
                null,
                "datasets/BEIS-Weekly-road-fuel-prices"
        )
        assert label2 == null
        assert url2 == null

        def (label3, url3) = JobHelpers.getMaybeInfoJsonLabelAndUrl(
                "git@github.com:GSS-Cogs/family-covid-19.git",
                "18ea9cb5a21a9e9f22af164ee1ab252083a2e845",
                null
        )
        assert label3 == null
        assert url3 == null
    }

}
