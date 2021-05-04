import json
import numpy as np
import pandas as pd

from gssutils import *

# +
# note: you can access a dict of this json via `scraper.seed`
info = 'info.json'

cubes = Cubes(info)
scraper = Scraper(seed=info)


# +
# Procedurally generate data
rng = np.random.default_rng(seed=1031)
df = pd.DataFrame(rng.integers(0, 100, size=(100, 4)), columns=list('ABCD'))

df.columns = ['Attribute', 'Dimension', 'Value', 'Measure']
df.loc[0:24,'Period'] = 'quarter/2020-q1'
df.loc[25:49, 'Period'] = 'quarter/2020-q2'
df.loc[50:74, 'Period'] = 'quarter/2020-q3'
df.loc[75:99, 'Period'] = 'quarter/2020-q4'

# +
for period in df['Period'].unique():


    if len(cubes.cubes) == 0:
        graph_uri = f"http://gss-data.org.uk/graph/gss_data/test/{scraper.seed['id']}"
        csv_name = scraper.seed['id']
        cubes.add_cube(scraper, df.loc[df['Period'] == period], csv_name)
    else:
        graph_uri = f"http://gss-data.org.uk/graph/gss_data/test/{scraper.seed['id']}/{period[-7:]}"
        csv_name = f"{scraper.seed['id']}-{period[-7:]}"
        cubes.add_cube(scraper, df.loc[df['Period'] == period], csv_name, override_containing_graph=graph_uri, suppress_catalog_and_dsd_output=True)




# -

cubes.output_all()


