package org.test;

import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping(value = "parent2", method = {GET,POST,DELETE})
public class ParentMappingClass2 {

	@RequestMapping
	public void nothing() {}
	
}
