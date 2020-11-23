#!/usr/bin/env python
# coding: utf-8
# %%

# %%


# -*- coding: utf-8 -*-
# # NISRA Weekly deaths,  Year   NI

from gssutils import *
import json
from dateutil.parser import parse
from datetime import datetime, timedelta
from urllib.parse import urljoin

#title = "Weekly deaths, 2020 (NI)"

scrape = Scraper('https://www.nisra.gov.uk/publications/weekly-deaths')
scrape


# %%



scrape.distributions = [x for x in scrape.distributions if x.mediaType == Excel]


# %%


dist = scrape.distributions[0]
display(dist)


# %%


tabs = { tab.name: tab for tab in dist.as_databaker() if tab.name.startswith('Table')}
list(tabs)

tidied_sheets = {}

for name, tab in tabs.items():

    if 'table 1' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(1, 4).fill(DOWN).is_not_blank() - remove

        measurement = cell.shift(2, 3).expand(RIGHT).is_not_blank() | cell.shift(2, 4).expand(RIGHT).is_not_blank()

        measure_type = 'Deaths'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDim(measurement, 'Measurement', DIRECTLY, ABOVE),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 2' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(0, 4).fill(RIGHT).is_not_blank() | tab.filter('Year to Date')

        gender = cell.shift(0, 5).expand(DOWN).is_not_blank()

        age = gender.shift(RIGHT).expand(DOWN).is_not_blank() - remove

        measure_type = 'Deaths'

        unit = 'Count'

        observations = age.fill(RIGHT).is_not_blank()

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, ABOVE),
            HDim(gender, 'Gender', CLOSEST, ABOVE),
            HDim(age, 'Age', DIRECTLY, LEFT),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 3' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = cell.shift(2, 4).expand(RIGHT).is_not_blank()

        week_ending = cell.shift(1, 4).fill(DOWN).is_not_blank() - remove

        measure_type = 'Deaths'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDim(area, 'Area', DIRECTLY, ABOVE),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 4' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(1, 3).fill(DOWN).is_not_blank() - remove

        place_of_death = cell.shift(2, 3).expand(RIGHT).is_not_blank()

        measure_type = 'Deaths'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDim(place_of_death, 'Place of Death', DIRECTLY, ABOVE),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 5' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(0, 4).fill(RIGHT).is_not_blank() | tab.filter('Year to Date')

        gender = cell.shift(0, 5).expand(DOWN).is_not_blank()

        age = gender.shift(RIGHT).expand(DOWN).is_not_blank() - remove

        measure_type = 'Covid Related Deaths'

        unit = 'Count'

        observations = age.fill(RIGHT).is_not_blank()

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, ABOVE),
            HDim(gender, 'Gender', CLOSEST, ABOVE),
            HDim(age, 'Age', DIRECTLY, LEFT),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 6' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = cell.shift(2, 4).expand(RIGHT).is_not_blank()

        week_ending = cell.shift(1, 4).fill(DOWN).is_not_blank() - remove

        measure_type = 'Covid Related Deaths'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDim(area, 'Area', DIRECTLY, ABOVE),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 7' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(1, 3).fill(DOWN).is_not_blank() - remove

        place_of_death = cell.shift(2, 3).expand(RIGHT).is_not_blank()

        measure_type = 'Covid Related Deaths'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDim(place_of_death, 'Place of Death', DIRECTLY, ABOVE),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 8' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = cell.shift(2, 4).expand(RIGHT).is_not_blank()

        week_ending = cell.shift(1, 4).fill(DOWN).is_not_blank() - remove

        measure_type = 'Covid Related Care Home Deaths'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDim(area, 'Area', DIRECTLY, ABOVE),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 9' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        period = cell.shift(0, 4).expand(DOWN).is_not_blank() - remove

        place_of_death = cell.shift(1, 3).expand(RIGHT).is_not_blank()

        measure_type = 'Covid Related Deaths'

        unit = 'Count'

        observations = period.fill(RIGHT).is_not_blank() - tab.filter('Cumulative Total').expand(DOWN)

        dimensions = [
            HDimConst('Area', area),
            HDim(period, 'Period', DIRECTLY, LEFT),
            HDim(place_of_death, 'Place of Death', DIRECTLY, ABOVE),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 10' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(1, 3).fill(DOWN).is_not_blank() - remove

        measure_type = 'Covid Related Death Occurrences'

        unit = 'Count'

        observations = week_ending.shift(RIGHT)

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 11' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(1, 3).fill(DOWN).is_not_blank() - remove

        place_of_death = cell.shift(2, 3).expand(RIGHT).is_not_blank()

        measure_type = 'Covid Related Death Occurrences'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDim(place_of_death, 'Place of Death', DIRECTLY, ABOVE),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 12' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        week_ending = cell.shift(1, 3).fill(DOWN).is_not_blank() - remove

        place_of_death = cell.shift(2, 3).expand(RIGHT).is_not_blank()

        measure_type = 'Covid Related Death Occurrences'

        unit = 'Count'

        observations = week_ending.fill(RIGHT).is_not_blank()

        dimensions = [
            HDimConst('Area', area),
            HDim(week_ending, 'Week Ending', DIRECTLY, LEFT),
            HDim(place_of_death, 'Place of Death', DIRECTLY, ABOVE),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()

    elif 'table 13' == name.lower():

        remove = tab.filter('P Weekly published data are provisional.').expand(DOWN).expand(RIGHT)

        cell = tab.filter('Contents')

        area = 'Northern Ireland'

        period = cell.shift(0, 4).expand(DOWN).is_not_blank() - remove

        place_of_death = cell.shift(1, 3).expand(RIGHT).is_not_blank()

        measure_type = 'Covid Related Death Occurrences'

        unit = 'Count'

        observations = period.fill(RIGHT).is_not_blank() - tab.filter('Cumulative Total').expand(DOWN)

        dimensions = [
            HDimConst('Area', area),
            HDim(period, 'Period', DIRECTLY, LEFT),
            HDim(place_of_death, 'Place of Death', DIRECTLY, ABOVE),
            HDimConst('Measure Type', measure_type),
            HDimConst('Unit', unit),
        ]

        tidy_sheet = ConversionSegment(tab, dimensions, observations)
        savepreviewhtml(tidy_sheet, fname="Preview.html")

        tidied_sheets[name] = tidy_sheet.topandas()


# %%


registrations_tables = {}

occurrences_tables = {}

for name in tidied_sheets:

    if name.lower() == 'table 1':

        df = tidied_sheets['Table 1']

        df['Unit'] = df.apply(lambda x: 'Average Count' if 'Average' in x['Measurement'] else x['Unit'], axis = 1)

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'},
                         'Measurement' : {'Average number of deaths registered in corresponding week over previous 5 years (2015 to 2019P)' : 'Average number of deaths registered in corresponding week over previous 5 years',
                                          'Average number of respiratory2 deaths registered in corresponding week over previous 5 years (2015 to 2019P)' : 'Average number of respiratory deaths registered in corresponding week over previous 5 years',
                                          'Covid-193 deaths registered in week (2020P)' : 'Covid-19 deaths registered in week',
                                          'Respiratory2 deaths registered in week (2020P)' : 'Respiratory deaths registered in week',
                                          'Total Number of Deaths Registered in Week (2020P)' : 'Total Number of Deaths Registered in Week'}})

        df['Age'] = 'all'

        df['Gender'] = 'T'

        df['Place of Death'] = 'total'

        df['Cause of Death'] = 'all'

        df['DATAMARKER'] = 'N/A'

        df = df.rename(columns={'OBS':'Value', 'Measurement' : 'Death Measurement Type',
                                'Week Ending':'Period'})

        registrations_tables['table 1'] = df

    elif 'table 2' in name.lower():

        df = tidied_sheets['Table 2']

        df['Gender'] = df.apply(lambda x: 'All' if 'Total Registered Deaths' in x['Gender'] else x['Gender'], axis = 1)

        indexNames = df['Week Ending'][ ~df['Week Ending'].isin(['Year to Date'])]
        indexNames = pd.to_datetime(indexNames)
        maxDate = parse(str(indexNames.max())).date()
        delta =  maxDate - parse('2020-01-04').date()

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D' if 'Year to Date' not in x['Week Ending'] else x['Week Ending'], axis = 1)

        df = df.replace({'Week Ending' : {'Year to Date' : 'gregorian-interval/2020-01-04T00:00:00/P' + str(delta.days) +'D'},
                         'Gender' : {'All' : 'T',
                                     'Female' : 'F',
                                     'Male ' : 'M'},
                         'Area' : {'Northern Ireland' : 'N07000001'}})

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df['Place of Death'] = 'total'

        df['Cause of Death'] = 'all'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        df['DATAMARKER'] = 'N/A'

        registrations_tables['table 2'] = df

    elif 'table 3' in name.lower():

        df = tidied_sheets['Table 3']

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Total' : 'N07000001'}})

        df['Age'] = 'all'

        df['Gender'] = 'T'

        df['Place of Death'] = 'total'

        df['Cause of Death'] = 'all'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        df['DATAMARKER'] = 'N/A'

        registrations_tables['table 3'] = df

    elif 'table 4' in name.lower():

        df = tidied_sheets['Table 4']

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'},
                         'Place of Death' : {'Care Home2' : 'Care Home',
                                             'Other3' : 'Other'}})

        df['Age'] = 'all'

        df['Gender'] = 'T'

        df['Cause of Death'] = 'all'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        df['DATAMARKER'] = 'N/A'

        registrations_tables['table 4'] = df

    elif 'table 5' in name.lower():

        df = tidied_sheets['Table 5']

        indexes = df.ix[df['Gender'].isin(['Total Registered Deaths']) & ~df['Week Ending'].isin(['Year to Date']) & df['Age'].isin(['All'])].index
        df.drop(indexes, inplace = True)

        df['Gender'] = df.apply(lambda x: 'All' if 'Total Registered Deaths' in x['Gender'] else x['Gender'], axis = 1)

        indexNames = df['Week Ending'][ ~df['Week Ending'].isin(['Year to Date'])]
        indexNames = pd.to_datetime(indexNames)
        maxDate = parse(str(indexNames.max())).date()
        delta =  maxDate - parse('2020-01-04').date()

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D' if 'Year to Date' not in x['Week Ending'] else x['Week Ending'], axis = 1)

        df = df.replace({'Week Ending' : {'Year to Date' : 'gregorian-interval/2020-01-04T00:00:00/P' + str(delta.days) +'D'},
                         'Gender' : {'All' : 'T',
                                     'Female' : 'F',
                                     'Male ' : 'M'},
                         'Area' : {'Northern Ireland' : 'N07000001'}})

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df['Cause of Death'] = 'covid-19-related'

        df['Place of Death'] = 'total'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        df['DATAMARKER'] = 'N/A'

        registrations_tables['table 5'] = df

    elif 'table 6' in name.lower():

        df = tidied_sheets['Table 6']

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Total' : 'N07000001'}})

        df['Age'] = 'all'

        df['Gender'] = 'T'

        df['Place of Death'] = 'total'

        df['Cause of Death'] = 'covid-19-related'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        registrations_tables['table 6'] = df

    elif 'table 7' in name.lower():

        df = tidied_sheets['Table 7']

        indexes = df.ix[df['Place of Death'].isin(['Total'])].index
        df.drop(indexes, inplace = True)

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'},
                         'Place of Death' : {'Care Home3' : 'Care Home',
                                             'Other4' : 'Other'}})

        df['Age'] = 'all'

        df['Gender'] = 'T'

        df['Cause of Death'] = 'covid-19-related'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        df['DATAMARKER'] = 'N/A'

        registrations_tables['table 7'] = df

    elif 'table 8' in name.lower():

        df = tidied_sheets['Table 8']

        indexes = df.ix[df['Area'].isin(['Total'])].index
        df.drop(indexes, inplace = True)

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Total' : 'N07000001'}})

        df['Age'] = 'all'

        df['Gender'] = 'T'

        df['Place of Death'] = 'care-home'

        df['Cause of Death'] = 'covid-19-related'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        registrations_tables['table 8'] = df

    elif 'table 9' in name.lower():

        df = tidied_sheets['Table 9']

        df['Period'] = df.apply(lambda x: 'gregorian-interval/' + x['Period'] + 'T00:00:00/P1D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'}})

        df['Age'] = 'all'

        df['Gender'] = 'T'

        df['Cause of Death'] = 'covid-19-related'

        df['Death Measurement Type'] = 'Total Number of Deaths Registered in Week'

        df['DATAMARKER'] = 'N/A'

        registrations_tables['table 9'] = df

    elif 'table 10' in name.lower():

        df = tidied_sheets['Table 10']

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'}})

        df['Place of Death'] = 'total'

        df['Cause of Death'] = 'covid-19-related'

        df['Residential Setting'] = 'all'

        occurrences_tables['table 10'] = df

    elif 'table 11' in name.lower():

        df = tidied_sheets['Table 11']

        indexes = df.ix[df['Place of Death'].isin(['Total'])].index
        df.drop(indexes, inplace = True)

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'},
                         'Place of Death' : {'Other3' : 'Other'}})

        df['Cause of Death'] = 'covid-19-related'

        df['Residential Setting'] = 'all'

        df['DATAMARKER'] = 'N/A'

        occurrences_tables['table 11'] = df

    elif 'table 12' in name.lower():

        df = tidied_sheets['Table 12']

        df['Unit'] = df.apply(lambda x: 'Percent' if '%' in x['Place of Death'] else x['Unit'], axis = 1)

        df['Measure Type'] = df.apply(lambda x: 'Percentage of all Covid Related Deaths' if '%' in x['Place of Death'] else x['Measure Type'], axis = 1)

        df['Place of Death'] = df.apply(lambda x: 'Hospital' if '% of all Covid-19 Hospital Deaths' in x['Place of Death'] else x['Place of Death'], axis = 1)

        df['Place of Death'] = df.apply(lambda x: 'Total' if '% of all Covid-19 Deaths' in x['Place of Death'] else x['Place of Death'], axis = 1)

        df['Week Ending'] = df.apply(lambda x: 'gregorian-interval/' + str(parse(x['Week Ending']).date() - timedelta(days=6)) +'T00:00:00/P7D', axis = 1)

        df = df.rename(columns={'OBS':'Value',
                                'Week Ending':'Period'})

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'},
                         'Place of Death' : {'Care Home3a' : 'Care Home',
                                             'Hospital3b' : 'Hospital'}})

        df['Residential Setting'] = 'care-home'

        df['Cause of Death'] = 'covid-19-related'

        occurrences_tables['table 12'] = df

    elif 'table 13' in name.lower():

        df = tidied_sheets['Table 13']

        df['Period'] = df.apply(lambda x: 'gregorian-interval/' + x['Period'] + 'T00:00:00/P1D', axis = 1)

        df = df.rename(columns={'OBS':'Value'})

        df = df.replace({'Area' : {'Northern Ireland' : 'N07000001'},
                         'Place of Death' : {'Care Home3' : 'Care Home',
                                             'Other4' : 'Other'}})

        df['Cause of Death'] = 'covid-19-related'

        df['Residential Setting'] = 'all'

        occurrences_tables['table 13'] = df

df


# %%


registrations = pd.concat(registrations_tables.values(), ignore_index=True)

registrations = registrations.rename(columns={'DATAMARKER':'Marker', 'Place of Death' : 'Location of Death'})

indexNames = registrations[ registrations['Unit'] == 'Average Count' ].index
registrations.drop(indexNames, inplace = True)

registrations = registrations.drop(['Measure Type', 'Unit'], axis=1)

registrations = registrations.replace({'Age' : {'>=7 days and < 1 year' : 'More than equal to 7 days and less than 1 year',
                                                '<7 days' : 'less-than-7-days',
                                                '85+' : '85 Plus'}})

for column in registrations:
    if column in ('Age', 'Death Measurement Type', 'Location of Death'):
        registrations[column] = registrations[column].map(lambda x: pathify(x))

registrations['Marker'] = registrations.apply(lambda x: '' if 'N/A' in str(x['Marker']) else x['Marker'], axis = 1)
registrations['Marker'] = registrations['Marker'].fillna('')
registrations['Value'] = registrations.apply(lambda x: '0' if '-' in str(x['Marker']) else x['Value'], axis = 1)
registrations['Value'] = pd.to_numeric(registrations['Value'], downcast='integer')

registrations = registrations.replace({'Area' : {
    'Antrim & Newtownabbey' : 'N09000001',
    'Ards & North Down' : 'N09000011',
    'Armagh City, Banbridge & Craigavon' : 'N09000002',
    'Belfast' : 'N09000003',
    'Causeway Coast & Glens' : 'N09000004',
    'Derry City & Strabane' : 'N09000005',
    'Fermanagh & Omagh' : 'N09000006',
    'Lisburn & Castlereagh' : 'N09000007',
    'Mid & East Antrim' : 'N09000008',
    'Mid Ulster' : 'N06000010',
    'Newry, Mourne & Down' : 'N09000010'},
    'Marker' : {
        '-' : ''}})

registrations = registrations[['Period', 'Death Measurement Type', 'Area', 'Gender', 'Age', 'Location of Death', 'Cause of Death', 'Value']]

registrations


# %%


csvName = 'registrations-observations.csv'
out = Path('out')
out.mkdir(exist_ok=True)
registrations.drop_duplicates().to_csv(out / csvName, index = False)

scrape.dataset.title = 'Weekly Deaths - Registrations'
scrape.dataset.family = 'covid-19'
dataset_path = pathify(os.environ.get('JOB_NAME', f'gss_data/{scrape.dataset.family}/' + Path(os.getcwd()).name)).lower() + '/registrations'
scrape.set_base_uri('http://gss-data.org.uk')
scrape.set_dataset_id(dataset_path)

scrape.dataset.comment = 'Weekly and daily death registrations in Northern Ireland including COVID-19 related deaths'
scrape.dataset.description = """
    Weekly and daily death registrations in Northern Ireland including COVID-19 related deaths
	Care Home deaths includes deaths in care homes only. Care home residents who have died in a different location will not be counted in this table.
    To meet user needs, NISRA publish timely but provisional counts of death registrations in Northern Ireland in our Weekly Deaths provisional dataset. Weekly totals are presented alongside a 5-year, weekly average as well as the minimum and maximum number of deaths for the same week over the last five years. To allow time for registration and processing, these figures are published 7 days after the week ends.
	Because of the coronavirus (COVID-19) pandemic, from 3rd April 2020, our weekly deaths release has been supplemented with the numbers of respiratory deaths (respiratory deaths include any death where Pneumonia, Bronchitis, Bronchiolitis or Influenza was mentioned anywhere on the death certificate); and deaths relating to COVID-19 (that is, where COVID-19 or suspected COVID-19 was mentioned anywhere on the death certificate, including in combination with other health conditions). The figures are presented by age group and sex.

	Find latest report here:
	https://www.nisra.gov.uk/publications/weekly-deaths

	Weekly published data are provisional.
	The majority of deaths are registered within five days in Northern Ireland.
	Respiratory deaths include any death where terms directly relating to respiratory causes were mentioned anywhere on the death certificate (this includes Covid-19 deaths).
	This is not directly comparable to the ONS figures relating to ‘deaths where the underlying cause was respiratory disease’.
	Covid-19 deaths include any death where Coronavirus or Covid-19 (suspected or confirmed) was mentioned anywhere on the death certificate.
	Data are assigned to LGD based on usual residence of the deceased, as provided by the informant. Usual residence can include a care home. Where the deceased was not usually resident in Northern Ireland, their death has been mapped to the place of death.
	The 'Other' category in Place of death includes deaths at a residential address which was not the usual address of the deceased and all other places.
	"""

csvw_transform = CSVWMapping()
csvw_transform.set_csv(out / csvName)
csvw_transform.set_mapping(json.load(open('info.json')))
csvw_transform.set_dataset_uri(urljoin(scrape._base_uri, f'data/{scrape._dataset_id}'))
csvw_transform.write(out / f'{csvName}-metadata.json')

with open(out / f'{csvName}-metadata.trig', 'wb') as metadata:
    metadata.write(scrape.generate_trig())



# %%


from IPython.core.display import HTML
for col in registrations:
    if col not in ['Value']:
        registrations[col] = registrations[col].astype('category')
        display(HTML(f"<h2>{col}</h2>"))
        display(registrations[col].cat.categories)


# %%


occurrences = pd.concat(occurrences_tables.values(), ignore_index=True)

occurrences = occurrences.rename(columns={'DATAMARKER':'Marker', 'Place of Death' : 'Location of Death'})

indexNames = occurrences[ occurrences['Unit'] == 'Percent' ].index
occurrences.drop(indexNames, inplace = True)

occurrences = occurrences.drop(['Measure Type', 'Unit'], axis=1)

for column in occurrences:
    if column in ('Location of Death'):
        occurrences[column] = occurrences[column].map(lambda x: pathify(x))

occurrences['Marker'] = occurrences.apply(lambda x: '' if 'N/A' in str(x['Marker']) else x['Marker'], axis = 1)
occurrences['Marker'] = occurrences['Marker'].fillna('')
occurrences['Value'] = occurrences.apply(lambda x: '0' if '-' in str(x['Marker']) else x['Value'], axis = 1)
occurrences['Value'] = pd.to_numeric(occurrences['Value'], downcast='integer')

occurrences = occurrences.replace({'Area' : {
    'Antrim & Newtownabbey' : 'N09000001',
    'Ards & North Down' : 'N09000011',
    'Armagh City, Banbridge & Craigavon' : 'N09000002',
    'Belfast' : 'N09000003',
    'Causeway Coast & Glens' : 'N09000004',
    'Derry City & Strabane' : 'N09000005',
    'Fermanagh & Omagh' : 'N09000006',
    'Lisburn & Castlereagh' : 'N09000007',
    'Mid & East Antrim' : 'N09000008',
    'Mid Ulster' : 'N06000010',
    'Newry, Mourne & Down' : 'N09000010'},
    'Marker' : {
        '-' : ''}})

occurrences = occurrences[['Period', 'Area', 'Location of Death', 'Cause of Death', 'Residential Setting', 'Value']]

occurrences


# %%


csvName = 'occurrences-observations.csv'
out = Path('out')
out.mkdir(exist_ok=True)
occurrences.drop_duplicates().to_csv(out / csvName, index = False)

scrape.dataset.title = 'Weekly deaths - Occurrences'
scrape.dataset.family = 'covid-19'

dataset_path = pathify(os.environ.get('JOB_NAME', f'gss_data/{scrape.dataset.family}/' + Path(os.getcwd()).name)).lower() + '/occurrences'
scrape.set_base_uri('http://gss-data.org.uk')
scrape.set_dataset_id(dataset_path)

scrape.dataset.comment = 'Weekly and daily death occurrences in Northern Ireland including COVID-19 related deaths'
scrape.dataset.description = """
    Weekly and daily death occurrences in Northern Ireland including COVID-19 related deaths
			This data is based on the actual date of death, from those deaths registered by GRO. All data in this table are subject to change, as some deaths will have occurred but haven’t been registered yet.
			Care home residents have been identified where either (a) the death occurred in a care home, or (b) the death occurred elsewhere but the place of usual residence of the deceased was recorded as a care home. It should be noted that the statistics will not capture those cases where a care home resident died in hospital or another location and the usual address recorded on their death certificate is not a care home.
    To meet user needs, NISRA publish timely but provisional counts of death registrations in Northern Ireland in our Weekly Deaths provisional dataset. Weekly totals are presented alongside a 5-year, weekly average as well as the minimum and maximum number of deaths for the same week over the last five years. To allow time for registration and processing, these figures are published 7 days after the week ends.
	Because of the coronavirus (COVID-19) pandemic, from 3rd April 2020, our weekly deaths release has been supplemented with the numbers of respiratory deaths (respiratory deaths include any death where Pneumonia, Bronchitis, Bronchiolitis or Influenza was mentioned anywhere on the death certificate); and deaths relating to COVID-19 (that is, where COVID-19 or suspected COVID-19 was mentioned anywhere on the death certificate, including in combination with other health conditions). The figures are presented by age group and sex.

	Find latest report here:
	https://www.nisra.gov.uk/publications/weekly-deaths

	Weekly published data are provisional.
	The majority of deaths are registered within five days in Northern Ireland.
	Respiratory deaths include any death where terms directly relating to respiratory causes were mentioned anywhere on the death certificate (this includes Covid-19 deaths).
	This is not directly comparable to the ONS figures relating to ‘deaths where the underlying cause was respiratory disease’.
	Covid-19 deaths include any death where Coronavirus or Covid-19 (suspected or confirmed) was mentioned anywhere on the death certificate.
	Data are assigned to LGD based on usual residence of the deceased, as provided by the informant. Usual residence can include a care home. Where the deceased was not usually resident in Northern Ireland, their death has been mapped to the place of death.
	The 'Other' category in Place of death includes deaths at a residential address which was not the usual address of the deceased and all other places.
	"""

csvw_transform = CSVWMapping()
csvw_transform.set_csv(out / csvName)
csvw_transform.set_mapping(json.load(open('info.json')))
csvw_transform.set_dataset_uri(urljoin(scrape._base_uri, f'data/{scrape._dataset_id}'))
csvw_transform.write(out / f'{csvName}-metadata.json')

with open(out / f'{csvName}-metadata.trig', 'wb') as metadata:
    metadata.write(scrape.generate_trig())


# %%


from IPython.core.display import HTML
for col in occurrences:
    if col not in ['Value']:
        occurrences[col] = occurrences[col].astype('category')
        display(HTML(f"<h2>{col}</h2>"))
        display(occurrences[col].cat.categories)


# %%




