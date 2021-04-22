# ---
# jupyter:
#   jupytext:
#     formats: ipynb,py:light
#     text_representation:
#       extension: .py
#       format_name: light
#       format_version: '1.5'
#       jupytext_version: 1.3.3
#   kernelspec:
#     display_name: Python 3
#     language: python
#     name: python3
# ---

# UK trade in services: all countries, non-seasonally adjusted

# +
from gssutils import *
import json
import numpy as np

df = pd.DataFrame()
cubes = Cubes("info.json")
scraper = Scraper(json.load(open('info.json'))['landingPage'])
# -

tabs = {tab.name: tab for tab in scraper.distribution(
    latest=True).as_databaker()}


# +
def left(s, amount):
    return s[:amount]


def right(s, amount):
    return s[-amount:]


def date_time(date):
    if len(date) == 4:
        return 'year/' + date
    elif len(date) == 6:
        return 'quarter/' + left(date, 4) + '-' + right(date, 2)
    else:
        return date


dfs = []

for name, tab in tabs.items():
    datasetTitle = 'uk-total-trade-all-countries-non-seasonally-adjusted'
    columns = ['Period', 'Country', 'Flow', 'Trade Type', 'Marker']

    if 'Index' in name or '7 Contact Sheet' in name:
        continue
    observations = tab.excel_ref('C7').expand(DOWN).expand(
        RIGHT).is_not_blank().is_not_whitespace()
    period = tab.excel_ref('C4').expand(
        RIGHT).is_not_blank().is_not_whitespace()
    flow = tab.fill(DOWN).one_of(['Exports', 'Imports'])
    country = tab.excel_ref('A7').expand(
        DOWN).is_not_blank().is_not_whitespace()
    trade_type = tab.excel_ref('B1')
    dimensions = [
        HDim(period, 'Period', DIRECTLY, ABOVE),
        HDim(country, 'Country', DIRECTLY, LEFT),
        HDim(flow, 'Flow', CLOSEST, ABOVE),
        HDim(trade_type, 'Trade Type', CLOSEST, LEFT),
    ]
    dfs.append(ConversionSegment(tab, dimensions, observations).topandas())
    break
    # savepreviewhtml(tidy_sheet, fname=tab.name + "Preview.html")

# -

# Post Processing
df = pd.concat(dfs).reset_index(drop=True)

df.rename(columns={'OBS': 'Value', 'DATAMARKER': 'Marker'}, inplace=True)
# df['Value'] = df['Value'].astype(float)
df['Value'] = pd.to_numeric(df.Value, errors='coerce')
df['Flow'] = df['Flow'].map(lambda s: s.lower().strip())
df["Country"] = df["Country"].map(lambda x: pathify(x).upper())
df['Trade Type'] = df['Trade Type'].apply(lambda x: 'total' if 'Total Trade' in x else
                                      ('goods' if 'Trade in Goods' in x else
                                       ('services' if 'Trade in Services' in x else x)))
df['Period'] = df['Period'].astype(str).replace('\.0', '', regex=True)
df['Period'] = df["Period"].apply(date_time)
df = df[['Period', 'Country', 'Flow', 'Trade Type', 'Value']]

# additional scraper info needed
scraper.dataset.family = 'trade'
add_to_des = """
These tables have been produced to provide an aggregated quarterly goods and services estimate and combines the most recent estimates for goods and services split by country.
Data for goods and services is consistent for annual whole world totals and quarters (from Q1 2016) with the trade data published in the Quarterly National Accounts, Quarterly Sector Accounts and Quarterly Balance of Payments on 30th September 2020.
These data are our best estimate of these bilateral UK trade flows. Users should note that alternative estimates are available, in some cases, via the statistical agencies for bilateral countries or through central databases such as:
UN Comtrade.
Some data for countries have been marked with N/A. This is because Trade in Goods do not collate data from these countries, therefore only Trade in Services is reflected within total trade for these countries
The data within these tables are also consistent with the below releases:
For Trade in Goods the data is consistent with UK Trade: August 2020 publication on 9th October 2020
For Trade in Services the data is consistent with UK Trade in services by partner country: April to June 2020 publication on 4th November 2020
"""
scraper.dataset.description = scraper.dataset.description + add_to_des

cubes.add_cube(scraper, df.drop_duplicates(), "ons-uk-total-trade")
cubes.output_all()
