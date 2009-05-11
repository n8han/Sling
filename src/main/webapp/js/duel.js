function makeHtml(md) {
  return new Showdown.converter().makeHtml('' + md) 
}

