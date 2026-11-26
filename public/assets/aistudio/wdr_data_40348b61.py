
import re
import requests
from datetime import datetime, timedelta

BASE = "http://217.174.229.206:8181"

AIRPORTS = [
    "UTAA", "UTAK", "UTAM", "UTAT",
    "UTAV", "UTAE", "UTAN"
]


class WDRData:

    def __init__(self, login, password):
        self.session = requests.Session()

        self.session.headers.update({
            "User-Agent": "Mozilla/5.0",
            "Cache-Control": "no-cache",
            "Pragma": "no-cache"
        })

        self.login = login
        self.password = password

    def get_links(self):

        response = self.session.post(
            BASE + "/cgi-bin/wdr",
            data={
                "user": self.login,
                "pass": self.password
            },
            timeout=30
        )

        response.raise_for_status()
        response.encoding = "koi8-r"

        from bs4 import BeautifulSoup

        soup = BeautifulSoup(
            response.text,
            "html.parser"
        )

        links = {
            "SATR": [],
            "FTTR": []
        }

        for a in soup.find_all("a"):

            href = a.get("href", "")

            name = (
                a.get_text(" ", strip=True)
                + " "
                + href
            ).upper()

            if "/DATA/METEO/" not in href.upper():
                continue

            if "SATR" in name:
                links["SATR"].append(href)

            elif "FTTR" in name:
                links["FTTR"].append(href)

        return links

    def message_datetime(self, href):

        filename = href.split("/")[-1]

        match = re.search(
            r"_(\d{6})$",
            filename
        )

        if not match:
            return datetime.min

        stamp = match.group(1)

        day = int(stamp[:2])
        hour = int(stamp[2:4])
        minute = int(stamp[4:6])

        now = datetime.now()

        for offset in (0, 1, 2):

            month = now.month - offset
            year = now.year

            while month <= 0:
                month += 12
                year -= 1

            try:
                candidate = datetime(
                    year,
                    month,
                    day,
                    hour,
                    minute
                )
            except ValueError:
                continue

            if candidate <= now + timedelta(hours=1):
                return candidate

        return datetime.min

    def newest_link(self, link_list):

        if not link_list:
            return None

        return max(
            link_list,
            key=self.message_datetime
        )

    def read_message(self, href):

        url = (
            href if href.startswith("http")
            else BASE + "/" + href.lstrip("/")
        )

        response = self.session.get(
            url,
            timeout=30,
            headers={
                "Cache-Control": "no-cache",
                "Pragma": "no-cache"
            }
        )

        response.raise_for_status()
        response.encoding = "koi8-r"

        return response.text.upper()

    def extract_report(self, text, kind, code):

        pattern = (
            r"\b" + kind + r"\s+" +
            code + r"\b.*?="
        )

        match = re.search(
            pattern,
            text,
            re.DOTALL
        )

        if match:
            return " ".join(
                match.group(0).split()
            )

        if kind == "TAF":

            nil_pattern = (
                r"\bTAF\s+" +
                code +
                r"\s+NIL\s*="
            )

            nil_match = re.search(
                nil_pattern,
                text
            )

            if nil_match:
                return " ".join(
                    nil_match.group(0).split()
                )

        return None

    def update(self):

        result = {
            code: {
                "METAR": "Maglumat ýok",
                "TAF": "Maglumat ýok"
            }
            for code in AIRPORTS
        }

        links = self.get_links()

        for kind in ("SATR", "FTTR"):

            candidates = links[kind]

            if not candidates:
                print(kind, "habary tapylmady")
                continue

            href = self.newest_link(candidates)

            if not href:
                continue

            message = self.read_message(href)

            report_type = (
                "METAR" if kind == "SATR"
                else "TAF"
            )

            for code in AIRPORTS:

                report = self.extract_report(
                    message,
                    report_type,
                    code
                )

                if report:
                    result[code][report_type] = report

            print(kind, "faýly:", href)

        return result
