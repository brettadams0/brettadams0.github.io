// Every fact here comes from the previous site, the résumé or a public repository.
// Nothing is added without a job or a repo behind it.

export const SITE_URL = 'https://brettadams.ca';

export const person = {
  name: 'Brett Adams',
  role: 'Software engineering student',
  description:
    'Software engineering student building backend services, data pipelines and developer tools. Computer Science at the University of Windsor, graduating 2027; Data Analyst Intern on Geotab’s Global Data Operations team.',
  resume: '/assets/resume/Resume_BrettAdams.pdf',
  github: 'https://github.com/brettadams0',
  linkedin: 'https://www.linkedin.com/in/bretta/',
  npm: 'https://www.npmjs.com/~adamsbrett00',
};

export type Link = { label: string; href: string; kind?: 'demo' | 'source' | 'case' | 'other' };

export type Project = {
  id: string;
  name: string;
  kind: string;
  live?: boolean;
  line: string;
  chips: string[];
  href: string; // where the card itself goes
  links: Link[];
  img: { src800: string; src1600: string; w: number; h: number; wide: number; alt: string; onWhite?: boolean };
  loop?: { mode: 'inview' | 'hover'; base: string; mp4Codec: string };
  featured?: boolean;
};

const img = (name: string, wide = 1600, w = 1600, h = 1000, big?: string) => ({
  src800: `/assets/img/${name}-800.webp`,
  src1600: big ?? `/assets/img/${name}-1600.webp`,
  w,
  h,
  wide,
  alt: '',
});

export const projects: Project[] = [
  {
    id: 'signal-lab',
    name: 'Signal Lab',
    kind: 'Interactive data & ML platform',
    live: true,
    featured: true,
    line: 'Explore historical Toronto transit incidents, UK retail revenue and synthetic customer retention, then reproduce one unusual day’s revenue from the published aggregates.',
    chips: ['React + TypeScript', 'Python + DuckDB', 'Offline-ready'],
    href: '/signal-lab/',
    links: [
      { label: 'Live demo', href: '/signal-lab/', kind: 'demo' },
      { label: 'Verify the revenue finding', href: '/projects/signal-lab/investigate/?alert=c646f48c31360b392784.weekday-1.median.net.2011-09-20', kind: 'other' },
      { label: 'Case study', href: '/projects/signal-lab/', kind: 'case' },
    ],
    img: { ...img('signal-lab', 1600, 1600, 1000, '/assets/img/signal-lab-overview.webp'), alt: 'Signal Lab’s Transit Intelligence workspace: incident counts, a 30-day incident chart and route activity bars.' },
  },
  {
    id: 'mcp-surface',
    name: 'mcp-surface',
    kind: 'MCP contract testing & CI',
    live: true,
    featured: true,
    line: 'Inspects what MCP servers actually expose, flags malformed tool schemas and unexpected changes, and guards CI. Verified against all 33 Chrome DevTools MCP tools.',
    chips: ['TypeScript', 'JSON Schema 2020-12', 'GitHub Action', 'npm'],
    href: '/mcp-surface/',
    links: [
      { label: 'Live demo', href: '/mcp-surface/', kind: 'demo' },
      { label: 'Case study', href: '/mcp/', kind: 'case' },
      { label: 'npm', href: 'https://www.npmjs.com/package/mcp-surface', kind: 'other' },
      { label: 'Source', href: 'https://github.com/brettadams0/mcp-surface', kind: 'source' },
    ],
    img: { ...img('mcp-surface-audit'), alt: 'The mcp-surface playground: a tools/list response beside the checker’s findings for it.' },
  },
  {
    id: 'neuronet-arena',
    name: 'neuronet-arena',
    kind: 'Neuroevolution, in your browser',
    live: true,
    line: 'Forty agents with their own neural networks evolve to forage. Watch any agent’s network fire as it moves.',
    chips: ['13.7× the food with selection on', 'JS matches Python bit for bit', 'No ML libraries'],
    href: '/neuronet-arena/',
    links: [
      { label: 'Live demo', href: '/neuronet-arena/', kind: 'demo' },
      { label: 'Case study', href: '/projects/neuronet-arena/', kind: 'case' },
      { label: 'Source', href: 'https://github.com/brettadams0/neuronet-arena', kind: 'source' },
    ],
    img: { ...img('neuronet-arena', 1280, 1280, 800, '/assets/img/neuronet-arena.webp'), alt: 'neuronet-arena at generation 202: agents foraging among food and hazards.' },
    loop: { mode: 'inview', base: '/assets/video/neuronet-arena-loop-480', mp4Codec: 'avc1.640016' },
  },
  {
    id: 'border-waits',
    name: 'border-waits',
    kind: 'Data pipeline · SQL in your browser',
    live: true,
    line: 'A pipeline over the wait estimates the Canadian and U.S. border agencies publish, read every few hours on GitHub’s scheduler. Query it with SQL in your browser.',
    chips: ['dbt + DuckDB', 'Tests gate every deploy', 'CBSA + U.S. CBP open data'],
    href: '/border-waits/',
    links: [
      { label: 'Live demo', href: '/border-waits/', kind: 'demo' },
      { label: 'Source', href: 'https://github.com/brettadams0/border-waits-pipeline', kind: 'source' },
    ],
    img: { ...img('border-waits'), alt: 'border-waits: the latest wait estimates for the Windsor–Detroit crossings, by direction.' },
  },
  {
    id: 'mazefinder',
    name: 'MazeFinder',
    kind: 'C++ compiled to WebAssembly',
    live: true,
    line: 'Watch 100 routes evolve, with breadth-first search guiding fitness and mutation. The browser runs the C++ in a Web Worker; the native CLI scores routes on a thread pool.',
    chips: ['20×20 to 40×40 mazes', 'C++ → WebAssembly', 'Seeded, reproducible'],
    href: '/mazefinder/',
    links: [
      { label: 'Live demo', href: '/mazefinder/', kind: 'demo' },
      { label: 'Source', href: 'https://github.com/brettadams0/MazeFinder-GeneticAlgorithm', kind: 'source' },
    ],
    img: { ...img('mazefinder'), alt: 'MazeFinder: an evolved route through a maze, beside a fitness-by-generation chart.' },
    loop: { mode: 'hover', base: '/assets/video/mazefinder-loop-640', mp4Codec: 'avc1.640016' },
  },
  {
    id: 'sift',
    name: 'Sift',
    kind: 'Android app',
    line: 'Swipe through your camera roll to keep or delete, then grade the keepers on-device. The cover is a real photo before and after Sift’s grading pipeline.',
    chips: ['Kotlin, 6 Gradle modules', 'Own baseline JPEG encoder', 'No INTERNET permission'],
    href: 'https://github.com/brettadams0/sift',
    links: [{ label: 'Source', href: 'https://github.com/brettadams0/sift', kind: 'source' }],
    img: { ...img('sift-grade'), alt: 'A rocket-launch photo split down the middle: the original, and Sift’s graded version.' },
  },
  {
    id: 'promptpal',
    name: 'PromptPal',
    kind: 'Web app',
    live: true,
    line: 'Write an AI prompt once as a template with fill-in fields. It flags empty required fields and leftover placeholders, and exports a ready-to-run Claude API call.',
    chips: ['TypeScript', 'Next.js', 'E2E + accessibility tests'],
    href: 'https://prompt-pal-gules.vercel.app',
    links: [
      { label: 'Live app', href: 'https://prompt-pal-gules.vercel.app', kind: 'demo' },
      { label: 'Source', href: 'https://github.com/brettadams0/PromptPal', kind: 'source' },
    ],
    img: { ...img('promptpal'), onWhite: true, alt: 'PromptPal’s builder: a code-review template with filled-in fields and a live preview.' },
  },
  {
    id: 'technest',
    name: 'TechNest',
    kind: 'PHP e-commerce store',
    line: 'A PHP store with no framework. Checkout is one transaction, so the last unit can’t sell twice.',
    chips: ['PHP', 'MySQL', '200+ tests'],
    href: 'https://github.com/brettadams0/TechNest',
    links: [{ label: 'Source', href: 'https://github.com/brettadams0/TechNest', kind: 'source' }],
    img: { ...img('technest'), onWhite: true, alt: 'The TechNest storefront home page.' },
  },
];

export type Job = {
  id: string;
  title: string;
  org: string;
  when: string;
  where: string;
  current?: boolean;
  points: string[];
  more?: string[];
  chips: string[];
};

export const jobs: Job[] = [
  {
    id: 'geotab',
    title: 'Data Analyst Intern, Global Data Operations',
    org: 'Geotab',
    when: 'May 2026 – Present',
    where: 'Oakville, ON',
    current: true,
    points: [
      'Own production pipeline changes on BigQuery and Apache Airflow end to end: root-cause write-up, SQL and DAG fix, staging validation and production checks.',
      'Built and maintain a multi-source data-retirement system used to safely decommission unused warehouse tables and pipelines.',
    ],
    more: [
      'Designed the team’s tiered review gate for GitLab merge requests, with a stricter path for production data changes.',
      'Run live schema changes and table migrations against fixed infrastructure deadlines, coordinating with dataset owners.',
      'Automate team workflow in Python (with unit tests) and PowerShell, integrating issue-tracker, service-desk and Google Workspace APIs.',
    ],
    chips: ['BigQuery', 'Apache Airflow', 'Cloud Composer', 'SQL', 'Python', 'GitLab CI/CD'],
  },
  {
    id: 'mto',
    title: 'Data Management Associate, Co-op',
    org: 'Ontario Ministry of Transportation',
    when: 'Jan – Aug 2025',
    where: 'St. Catharines, ON',
    points: [
      'Built Power BI dashboards on Azure Databricks and SQL that replaced manual reporting across business units.',
      'Automated legacy workflows with SQL queries and VBA scripts, cutting processing time from hours to minutes.',
    ],
    more: ['Led the rollout of the internal Data Broker Tool, onboarding teams and standardizing the data vocabulary.'],
    chips: ['Power BI', 'Azure Databricks', 'SQL', 'VBA'],
  },
  {
    id: 'cimco-2024',
    title: 'Digital Solutions, Co-op',
    org: 'CIMCO Refrigeration',
    when: 'Apr – Sep 2024',
    where: 'Burlington, ON',
    points: [
      'Built Flask web applications that deliver real-time data and alarm information to NHL arena operations teams.',
      'Designed and ran hands-on Python training sessions for over 20 employees.',
      'Automated controller data collection with a Python tool, replacing manual data entry.',
    ],
    more: ['Wrote a Python pipeline that aggregates data from two APIs into structured JSON for the arena interface.'],
    chips: ['Python', 'Flask', 'JavaScript'],
  },
  {
    id: 'cimco-2023',
    title: 'Digital Solutions',
    org: 'CIMCO Refrigeration',
    when: 'Apr – Sep 2023',
    where: 'Burlington, ON',
    points: [
      'Built Power Platform tools adopted by several departments to automate repetitive manual workflows.',
      'Automated SharePoint document routing and approvals, cutting turnaround time between departments.',
    ],
    chips: ['Power Platform', 'SharePoint'],
  },
];

export const skills: { group: string; items: string[] }[] = [
  { group: 'Languages', items: ['Python', 'SQL', 'TypeScript', 'JavaScript', 'PHP', 'Java', 'C++', 'C', 'PowerShell'] },
  { group: 'Data', items: ['BigQuery', 'Apache Airflow', 'Cloud Composer', 'Azure Databricks', 'dbt', 'DuckDB', 'Power BI', 'Superset', 'MySQL'] },
  { group: 'Tools', items: ['Git', 'GitLab CI/CD', 'GitHub Actions', 'Docker', 'Linux', 'Node.js'] },
  { group: 'Web', items: ['Flask', 'React', 'Next.js', 'HTML/CSS'] },
];

export const education = {
  school: 'University of Windsor',
  program: 'Honours Computer Science, Software Engineering Specialization (Co-op)',
  when: '2022 – 2027',
  where: 'Windsor, ON',
};
