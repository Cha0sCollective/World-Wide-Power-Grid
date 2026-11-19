"""Campaign selection and identity checks; starts no Gradle or Minecraft."""
import copy
import unittest
from unittest.mock import patch

import cee_headless_campaign as campaign
import test_suites as suites


class CeeCampaignChecks(unittest.TestCase):
    def test_default_recommendation_and_full_suite_remain_unchanged(self):
        original=suites.catalog()
        self.assertEqual(original['recommended_runtime']['electroenergetics'],'1.21.1-1.1.4')
        self.assertEqual(len(suites.plan('full-headless')),342)
        campaign.campaign_catalog()
        self.assertEqual(suites.catalog(),original)

    def test_development_matrix_has_both_platforms_solvers_and_profiles(self):
        spec=campaign.campaign_catalog();jobs=suites.plan('full-headless',spec=spec)
        self.assertEqual(len(jobs),398)
        self.assertEqual({(j['platform'],j['backend'],j['profile']) for j in jobs if 'backend' in j},
                         {(p,b,f) for p in spec['platforms'] for b in spec['backends'] for f in spec['profiles']})
        self.assertEqual({j['loader'] for j in jobs if 'loader' in j},{'21.1.262'})
        self.assertTrue(all(j['lane']=='headless' for j in jobs))

    def test_development_keeps_every_baseline_job_and_all_cee_groups(self):
        baseline={j['id']:j for j in suites.plan('full-headless')}
        jobs={j['id']:j for j in suites.plan('full-headless',spec=campaign.campaign_catalog())}
        self.assertTrue(baseline.keys()<=jobs.keys())
        self.assertTrue(all(jobs[key]==job for key,job in baseline.items()))
        names={name for job in jobs.values() for name in job.get('namespaces',[])}
        self.assertTrue({'wwpg_cee_readiness','wwpg_cee_migration','wwpg_cee_lifecycle','wwpg_cee_ownership',
                         'wwpg_cee_adapters','wwpg_cee_baseline','wwpg_cee_baseline_train','wwpg_cee_fans'}<=names)
        self.assertNotIn('wwpg_cee_migration_cold',names)

    def test_development_catalog_is_fully_classified(self):
        with patch.object(suites,'catalog',side_effect=campaign.campaign_catalog):
            self.assertGreaterEqual(suites.audit()['namespaces'],83)

    def test_another_player_or_version_is_rejected(self):
        inputs={'version':campaign.VERSION,'artifacts':{'runtime':{'sha256':campaign.PLAYER_SHA256}}}
        campaign.assert_player(inputs)
        for field in ('version','hash'):
            bad=copy.deepcopy(inputs)
            if field=='version':bad['version']='another version'
            else:bad['artifacts']['runtime']['sha256']='a'*64
            with self.assertRaisesRegex(ValueError,'exact frozen'):
                campaign.assert_player(bad)


if __name__=='__main__':unittest.main()
