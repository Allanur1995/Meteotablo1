
from getpass import getpass
from wdr_data import WDRData, AIRPORTS

print("=== METEO WDR TEST ===")

login = input("WDR Login: ")
password = getpass("WDR Password: ")

wdr = WDRData(login, password)

try:
    print("\nWDR bilen baglanyşyk barlanýar...")

    result = wdr.update()

    print("\n========== METAR ==========")

    for code in AIRPORTS:
        print("\n", code)
        print(result[code]["METAR"])

    print("\n========== TAF ==========")

    for code in AIRPORTS:
        print("\n", code)
        print(result[code]["TAF"])

    print("\n=== TEST TAMAMLANDY ===")

except Exception as error:
    print("\nÝALŇYŞLYK:")
    print(str(error))

input("\nÇykmak üçin Enter bas...")
