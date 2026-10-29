import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "optimize"))
import radio_catalog as rc


def st(name, url, votes=1, tags="", ok=1, codec="MP3", hls=0, uuid="abcdef0123"):
    return {"name": name, "url_resolved": url, "votes": votes, "tags": tags, "lastcheckok": ok,
            "codec": codec, "hls": hls, "stationuuid": uuid, "state": ""}


class FamilyAndSections(unittest.TestCase):
    def test_adult_is_refused_by_name_or_tag(self):
        self.assertFalse(rc.is_family_safe("Sexy FM", ""))
        self.assertFalse(rc.is_family_safe("Night", "pop,adult"))
        self.assertTrue(rc.is_family_safe("France Inter", "news,talk"))

    def test_sections(self):
        self.assertEqual(rc.section_of("Radio Coran", ""), "religion")
        self.assertEqual(rc.section_of("X", "news,talk"), "news")
        self.assertEqual(rc.section_of("Foot FM", "football"), "sport")
        self.assertEqual(rc.section_of("Classique", "classical"), "culture")
        self.assertEqual(rc.section_of("Hit", "pop"), "music")

    def test_kind(self):
        self.assertEqual(rc.kind_of("MP3", 0), "MP3")
        self.assertEqual(rc.kind_of("AAC+", 0), "AAC")
        self.assertEqual(rc.kind_of("UNKNOWN", 1), "HLS")


class Pick(unittest.TestCase):
    def test_keeps_the_most_voted_and_drops_duplicates_dead_and_adult(self):
        raw = [st("A", "https://a/1", 5), st("A", "https://a/2", 9), st("B", "https://a/1", 3),
               st("Dead", "https://d/1", 99, ok=0), st("Porn FM", "https://p/1", 50),
               st("C", "ftp://c/1", 7), st("Plain", "http://h/1", 8), st("D", "https://d/2", 2)]
        out = rc.pick(raw)
        self.assertEqual([s["n"] for s in out], ["A", "B", "D"])   # the second «A» is a duplicate name, «B» keeps the free url, http is refused
        self.assertEqual(out[0]["u"], "https://a/2")   # the most voted «A»


class Build(unittest.TestCase):
    def test_only_streams_that_play_and_sorted_by_section(self):
        raw = [st("Music1", "https://m/1", 9, "pop"), st("News1", "https://n/1", 8, "news"),
               st("Down", "https://x/1", 7), st("Quran1", "https://q/1", 6, "quran"), st("Music2", "https://m/2", 5, "pop")]
        out = rc.build_country("XX", tester=lambda u: "x/" not in u, fetch=lambda cc: raw)
        self.assertEqual([s["n"] for s in out["stations"]], ["News1", "Music1", "Music2", "Quran1"])
        self.assertEqual(out["cc"], "XX")

    def test_cap_after_the_test(self):
        raw = [st("S%d" % i, "https://x/%d" % i, 1000 - i) for i in range(rc.MAX_PER_COUNTRY + 30)]
        out = rc.build_country("XX", tester=lambda u: True, fetch=lambda cc: raw)
        self.assertEqual(len(out["stations"]), rc.MAX_PER_COUNTRY)


class PublishGuards(unittest.TestCase):
    def test_a_thin_list_is_not_published(self):
        self.assertFalse(rc.acceptable({"stations": [{}] * (rc.MIN_STATIONS - 1)}))
        self.assertTrue(rc.acceptable({"stations": [{}] * rc.MIN_STATIONS}))
        self.assertFalse(rc.acceptable({}))

    def test_a_failed_country_keeps_the_last_good_file_and_fails_the_run(self):
        import json
        import tempfile
        with tempfile.TemporaryDirectory() as out:
            good = os.path.join(out, "fr.json")
            with open(good, "w", encoding="utf-8") as f:
                f.write('{"v":1,"cc":"FR","stations":[1]}')
            real = rc.build_country
            rc.build_country = lambda cc, **k: {"v": 1, "cc": cc, "stations": [{}]}   # a bad day: one radio alive
            try:
                code = rc.main(["x", out, "FR"])
            finally:
                rc.build_country = real
            self.assertEqual(code, 1)
            self.assertEqual(json.load(open(good, encoding="utf-8"))["stations"], [1])
            self.assertFalse(os.path.exists(good + ".part"))


if __name__ == "__main__":
    unittest.main()
