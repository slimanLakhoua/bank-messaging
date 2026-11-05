"""Validate the contents of the distributable archive using synthetic fixtures."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile


class PackageReleaseTest(unittest.TestCase):
    def test_archive_excludes_local_instructions_and_keeps_runnable_delivery(self):
        script = Path(__file__).with_name('package-release.py')
        spec = importlib.util.spec_from_file_location('package_release', script)
        package = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(package)
        project = json.loads((script.parents[1] / 'frontend/package.json').read_text())['name']
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            package.ROOT = root
            required = {
                'backend/target/messaging-0.0.1-SNAPSHOT.jar',
                f'frontend/dist/{project}/browser/index.html',
                'backend/mvnw',
                'backend/.mvn/wrapper/maven-wrapper.properties',
                'frontend/e2e/live.spec.ts',
                'docs/TESTS.md',
                'docs/LIVRAISON.md',
                '.github/workflows/ci.yml',
            }
            private = {
                'AGENTS.md', '.agents/skills/local/SKILL.md', '.codex/agents/local.toml',
                'docs/AGENTS-ET-SKILLS.md', 'docs/PLAN.md', 'docs/SUJET.md',
                'docs/AUDIT.md', 'docs/REVUE-HEXAGONALE.md', 'docs/REVUE-VALIDATION.md',
                'docs/VALIDATION-P6.json', 'docs/VALIDATION-REVUE.json',
                'backend/AGENTS.md', 'backend/.env', 'frontend/.env.local',
                'backend/mqjms.log.0', 'backend/mqjms.log.0.lck',
                'frontend/.angular/cache/private', 'backend/.vscode/settings.json',
                'scripts/open-mq-explorer.sh',
            }
            for name in set(package.ROOT_FILES) | required | private:
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('synthetic fixture\n')
            with contextlib.redirect_stdout(io.StringIO()):
                package.main()
            archive, = (root / 'release').glob('*.zip')
            with zipfile.ZipFile(archive) as delivery:
                names = set(delivery.namelist())
                self.assertFalse(names & private, f'Private files included: {names & private}')
                self.assertTrue(required <= names, f'Missing delivery files: {required - names}')
                manifest = json.loads(delivery.read('MANIFEST.json'))
                self.assertEqual(set(manifest['files']), names - {'MANIFEST.json'})
                for name, digest in manifest['files'].items():
                    self.assertEqual(hashlib.sha256(delivery.read(name)).hexdigest(), digest)
            checksum = (root / 'release/SHA256SUMS').read_text().split()[0]
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(), checksum)


if __name__ == '__main__':
    unittest.main()
