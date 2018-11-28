{
  local labsjdk8 = {name: 'labsjdk', version: '8u172-jvmci-0.48', platformspecific: true},

  local oraclejdk11 = {name : 'oraclejdk', version : "11+20", platformspecific: true},

  jdk8: {
    downloads+: {
      JAVA_HOME: labsjdk8,
      JDT: {name: 'ecj', version: '4.5.1', platformspecific: false},
    },
  },

  jdk11: {
    downloads+: {
      EXTRA_JAVA_HOMES: labsjdk8,
      JAVA_HOME: oraclejdk11,
    },
  },

  deploy:      {targets: ['deploy']},
  gate:        {targets: ['gate']},
  postMerge:   {targets: ['post-merge']},
  bench:       {targets: ['bench', 'post-merge']},
  dailyBench:  {targets: ['bench', 'daily']},
  weeklyBench: {targets: ['bench', 'weekly']},

  local common = {
    packages+: {
      'pip:astroid': '==1.1.0',
      'pip:pylint': '==1.1.0',
    },
    catch_files+: [
      'Graal diagnostic output saved in (?P<filename>.+.zip)',
      'npm-debug.log', // created on npm errors
    ],
  },

  linux: common + {
    packages+: {
      'apache/ab': '==2.3',
      binutils: '==2.23.2',
      gcc: '==4.9.1',
      git: '>=1.8.3',
      maven: '==3.3.9',
      valgrind: '>=3.9.0',
    },
    capabilities+: ['linux', 'amd64'],
  },

  ol65: self.linux + {
    capabilities+: ['ol65'],
  },

  x52: self.linux + {
    capabilities+: ['no_frequency_scaling', 'tmpfs25g', 'x52'],
  },

  sparc: common + {
    capabilities: ['solaris', 'sparcv9'],
  },

  darwin: common + {
    environment+: {
      // for compatibility with macOS El Capitan
      MACOSX_DEPLOYMENT_TARGET: '10.11',
    },
    capabilities: ['darwin_sierra', 'amd64'],
  },
}
